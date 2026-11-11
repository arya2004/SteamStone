package steamstone.steam.nativeapi.worker;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamNativeHandle;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import steamstone.concurrent.StoneFutures;
import steamstone.exception.StoneException;
import steamstone.steam.nativeapi.commit.AchievementCommit;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Response;
import steamstone.steam.nativeapi.protocol.Status;

/**
 * One game/account per process. Never initializes Steam in the JavaFX process.
 */
public final class NativeWorker {
    private NativeWorker() {
    }

    public static void main(final String[] args) {
        if (args.length == 1 && args[0].equals("--check-runtime")) {
            checkRuntime();
            return;
        }
        if (args.length != 2) {
            throw new StoneException("Expected request and response paths.");
        }
        final var json = new ObjectMapper();
        try {
            final var request = json.readValue(Path.of(args[0]).toFile(), Request.class);
            final var response =
                    StoneFutures.await(CompletableFuture.completedFuture(request).thenApply(NativeWorker::run)
                            .exceptionally((final var failure) -> new Response(request.appId(), request.steamId(),
                                    Status.UNKNOWN,
                                    "Could not confirm the Steam session result. Keep Steam running, sign in to the " +
                                            "selected "
                                            + "account, and reload before retrying; changes may still sync.",
                                    Map.of())));
            json.writeValue(Path.of(args[1]).toFile(), response);
        } catch (final IOException exception) {
            throw new StoneException("Could not read or write the Steam worker protocol files.", exception);
        }
    }

    private static void checkRuntime() {
        try {
            final var loader = new SteamNativeLibraries();
            SteamAPI.loadLibraries(loader);
            try (final var arena = Arena.ofConfined()) {
                final var symbols = SymbolLookup.libraryLookup(loader.path("steam_api"), arena);
                for (final var symbol : List.of("SteamAPI_SteamUserStats_v013",
                        "SteamAPI_ISteamUserStats_GetAchievementDisplayAttribute",
                        "SteamAPI_ISteamUserStats_GetAchievementAndUnlockTime")) {
                    if (symbols.find(symbol).isEmpty()) {
                        throw new StoneException("Missing Steam API symbol: " + symbol);
                    }
                }
            }
            System.out.println("Bundled Steam native runtime loaded; no Steam session opened.");
        } catch (final RuntimeException | LinkageError exception) {
            throw new StoneException("Could not load the bundled Steam native runtime.", exception);
        }
    }

    public static Response run(final Request request) {
        try {
            final var loader = new SteamNativeLibraries();
            SteamAPI.loadLibraries(loader);
            final var result = SteamAPI.initEx();
            if (result != SteamAPI.InitResult.OK) {
                return new Response(request.appId(), request.steamId(), Status.REJECTED,
                        "Steam initialization failed (" + result
                                + "). Keep Steam running and check this game's local Steam availability.", Map.of());
            }
            try (final var session = new NativeSession(request.appId());
                 final var attributes = new SteamAttributes(loader.path("steam_api"))) {
                final var actualUser = Long.toUnsignedString(
                        SteamNativeHandle.getNativeHandle(session.user().getSteamID()));
                if (Integer.toUnsignedLong(session.utils().getAppID()) != request.appId()
                        || !actualUser.equals(request.steamId())) {
                    return new Response(request.appId(), request.steamId(), Status.REJECTED,
                            "The running Steam account or game does not match this editor. No changes were applied.",
                            Map.of());
                }
                // Since SDK 1.61, Steam preloads current-user stats at initialization.
                final var deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
                while (session.stats().getNumAchievements() == 0 && System.nanoTime() < deadline) {
                    SteamAPI.runCallbacks();
                    Thread.sleep(50);
                }
                // Drain initial events before a possible StoreStats call.
                SteamAPI.runCallbacks();
                return new AchievementCommit().execute(request,
                        new NativeAchievementStats(session.stats(), attributes, session.callback()));
            }
        } catch (final SteamException exception) {
            throw new StoneException("Could not initialize Steam.", exception);
        } catch (final InterruptedException exception) {
            throw new StoneException("The Steam session was interrupted. Changes may still sync.", exception);
        } catch (final RuntimeException | LinkageError exception) {
            throw new StoneException("Could not complete the Steam session. Changes may still sync.", exception);
        }
    }
}
