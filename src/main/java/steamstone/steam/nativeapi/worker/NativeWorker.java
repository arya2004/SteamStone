package steamstone.steam.nativeapi.worker;

import java.util.List;

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

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;
import steamstone.steam.nativeapi.commit.AchievementCommit;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Response;
import steamstone.steam.nativeapi.protocol.Status;

/**
 * One game/account per process. Never initializes Steam in the JavaFX process.
 */
public final class NativeWorker {
    private static final int CHECK_RUNTIME_ARGUMENT_COUNT = 1;
    private static final int FIRST_ARGUMENT_INDEX = 0;
    private static final int PROTOCOL_ARGUMENT_COUNT = 2;
    private static final int RESPONSE_ARGUMENT_INDEX = 1;
    private static final String LIBRARY_NAME = "steam_api";
    private static final String USER_STATS_SYMBOL = "SteamAPI_SteamUserStats_v013";
    private static final String ACHIEVEMENT_ATTRIBUTE_SYMBOL =
            "SteamAPI_ISteamUserStats_GetAchievementDisplayAttribute";
    private static final String ACHIEVEMENT_STATE_SYMBOL = "SteamAPI_ISteamUserStats_GetAchievementAndUnlockTime";
    private static final List<String> REQUIRED_SYMBOLS = List.of(
            USER_STATS_SYMBOL, ACHIEVEMENT_ATTRIBUTE_SYMBOL, ACHIEVEMENT_STATE_SYMBOL);
    private static final long CALLBACK_POLL_INTERVAL_MILLIS = 50;

    private static final Duration INITIAL_STATS_TIMEOUT = Duration.ofSeconds(12);

    public static void main(final String[] args) {
        if (args.length == CHECK_RUNTIME_ARGUMENT_COUNT &&
                args[FIRST_ARGUMENT_INDEX].equals(
                        NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT)) {
            checkRuntime();
            return;
        }
        if (args.length != PROTOCOL_ARGUMENT_COUNT) {
            throw new StoneException(ErrorCode.INVALID_INPUT);
        }
        final var json = new ObjectMapper();
        try {
            final var request =
                    json.readValue(Path.of(args[FIRST_ARGUMENT_INDEX]).toFile(), Request.class);
            json.writeValue(Path.of(args[RESPONSE_ARGUMENT_INDEX]).toFile(), run(request));
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.STEAM_SESSION, e);
        }
    }

    private static void checkRuntime() {
        try (final var arena = Arena.ofConfined()) {
            final var loader = new SteamNativeLibraries();
            SteamAPI.loadLibraries(loader);
            final var symbols = SymbolLookup.libraryLookup(loader.path(LIBRARY_NAME), arena);
            for (final var symbol : REQUIRED_SYMBOLS) {
                if (symbols.find(symbol).isEmpty()) {
                    throw new StoneException(ErrorCode.NATIVE_RUNTIME);
                }
            }
            System.out.println("Bundled Steam native runtime loaded; no Steam session opened.");
        } catch (final RuntimeException | LinkageError e) {
            throw StoneException.wrap(ErrorCode.NATIVE_RUNTIME, e);
        }
    }

    public static Response run(final Request request) {
        try {
            final var loader = new SteamNativeLibraries();
            SteamAPI.loadLibraries(loader);
            final var result = SteamAPI.initEx();
            if (result != SteamAPI.InitResult.OK) {
                return new Response(request.appId(), request.steamId(), Status.REJECTED,
                        ErrorCode.STEAM_SESSION, Map.of());
            }
            try (final var session = new NativeSession(request.appId());
                 final var attributes = new SteamAttributes(loader.path(LIBRARY_NAME))) {
                final var actualUser = Long.toUnsignedString(
                        SteamNativeHandle.getNativeHandle(session.user().getSteamID()));
                if (Integer.toUnsignedLong(session.utils().getAppID()) != request.appId()
                        || !actualUser.equals(request.steamId())) {
                    return new Response(request.appId(), request.steamId(), Status.REJECTED,
                            ErrorCode.STEAM_SESSION, Map.of());
                }
                // Since SDK 1.61, Steam preloads current-user stats at initialization.
                final var deadline = System.nanoTime() + INITIAL_STATS_TIMEOUT.toNanos();
                while (session.stats().getNumAchievements() == 0 && System.nanoTime() < deadline) {
                    SteamAPI.runCallbacks();
                    Thread.sleep(CALLBACK_POLL_INTERVAL_MILLIS);
                }
                // Drain initial events before a possible StoreStats call.
                SteamAPI.runCallbacks();
                return new AchievementCommit().execute(request,
                        new NativeAchievementStats(session.stats(), attributes, session.callback()));
            }
        } catch (final SteamException | InterruptedException | RuntimeException | LinkageError e) {
            throw StoneException.wrap(ErrorCode.STEAM_SESSION, e);
        }
    }
}
