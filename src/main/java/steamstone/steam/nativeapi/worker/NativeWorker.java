package steamstone.steam.nativeapi.worker;

import steamstone.steam.nativeapi.NativeRuntimeConstants;
import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamNativeHandle;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.time.Duration;
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
    private static final Duration INITIAL_STATS_TIMEOUT = Duration.ofSeconds(12);


    public static void main(final String[] args) {
        if (args.length == NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT_COUNT &&
                args[NativeRuntimeConstants.FIRST_ARGUMENT_INDEX].equals(
                        NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT)) {
            checkRuntime();
            return;
        }
        if (args.length != NativeRuntimeConstants.PROTOCOL_ARGUMENT_COUNT) {
            throw new StoneException("Expected request and response paths.");
        }
        final var json = new ObjectMapper();
        try {
            final var request =
                    json.readValue(Path.of(args[NativeRuntimeConstants.FIRST_ARGUMENT_INDEX]).toFile(), Request.class);
            final var response =
                    StoneFutures.await(CompletableFuture.completedFuture(request).thenApply(NativeWorker::run)
                            .exceptionally((final var failure) -> new Response(request.appId(), request.steamId(),
                                    Status.UNKNOWN,
                                    "Could not confirm the Steam session result. Keep Steam running, sign in to the " +
                                            "selected "
                                            + "account, and reload before retrying; changes may still sync.",
                                    Map.of())));
            json.writeValue(Path.of(args[NativeRuntimeConstants.RESPONSE_ARGUMENT_INDEX]).toFile(), response);
        } catch (final IOException exception) {
            throw new StoneException("Could not read or write the Steam worker protocol files.", exception);
        }
    }

    private static void checkRuntime() {
        try {
            final var loader = new SteamNativeLibraries();
            SteamAPI.loadLibraries(loader);
            try (final var arena = Arena.ofConfined()) {
                final var symbols = SymbolLookup.libraryLookup(loader.path(SteamNativeConstants.LIBRARY_NAME), arena);
                for (final var symbol : SteamNativeConstants.REQUIRED_SYMBOLS) {
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
                 final var attributes = new SteamAttributes(loader.path(SteamNativeConstants.LIBRARY_NAME))) {
                final var actualUser = Long.toUnsignedString(
                        SteamNativeHandle.getNativeHandle(session.user().getSteamID()));
                if (Integer.toUnsignedLong(session.utils().getAppID()) != request.appId()
                        || !actualUser.equals(request.steamId())) {
                    return new Response(request.appId(), request.steamId(), Status.REJECTED,
                            "The running Steam account or game does not match this editor. No changes were applied.",
                            Map.of());
                }
                // Since SDK 1.61, Steam preloads current-user stats at initialization.
                final var deadline = System.nanoTime() + INITIAL_STATS_TIMEOUT.toNanos();
                while (session.stats().getNumAchievements() == 0 && System.nanoTime() < deadline) {
                    SteamAPI.runCallbacks();
                    Thread.sleep(SteamNativeConstants.CALLBACK_POLL_INTERVAL_MILLIS);
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
