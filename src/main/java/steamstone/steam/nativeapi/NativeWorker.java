package steamstone.steam.nativeapi;

import steamstone.exception.StoneException;
import com.codedisaster.steamworks.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import steamstone.concurrent.StoneFutures;

import static steamstone.steam.nativeapi.NativeProtocol.*;

/**
 * One game/account per process. Never initializes Steam in the JavaFX process.
 */
public final class NativeWorker {
    public static void main(String[] args) {
        try {
            if (args.length == 1 && args[0].equals("--check-runtime")) {
                var loader = new SteamNativeLibraries();
                SteamAPI.loadLibraries(loader);
                try (var arena = java.lang.foreign.Arena.ofConfined()) {
                    var symbols = java.lang.foreign.SymbolLookup.libraryLookup(loader.path("steam_api"), arena);
                    for (String symbol : java.util.List.of("SteamAPI_SteamUserStats_v013",
                            "SteamAPI_ISteamUserStats_GetAchievementDisplayAttribute",
                            "SteamAPI_ISteamUserStats_GetAchievementAndUnlockTime")) {
                        if (symbols.find(symbol).isEmpty())
                            throw new StoneException("Missing Steam API symbol: " + symbol);
                    }
                }
                System.out.println("Bundled Steam native runtime loaded; no Steam session opened.");
                return;
            }
            if (args.length != 2)
                throw new StoneException("Expected request and response paths.");
            var json = new ObjectMapper();
            Request request = json.readValue(Path.of(args[0]).toFile(), Request.class);
            Response response = StoneFutures.await(CompletableFuture.completedFuture(request)
                    .thenApply(NativeWorker::run).exceptionally(failure -> new Response(
                            request.appId(), request.steamId(), Status.UNKNOWN,
                            "Could not confirm the Steam session result. Keep Steam running, sign in to the selected "
                                    + "account, and reload before retrying; changes may still sync.",
                            java.util.Map.of())));
            json.writeValue(Path.of(args[1]).toFile(), response);
        } catch (IOException ex) {
            throw new StoneException("Could not read or write the Steam worker protocol files.", ex);
        } catch (RuntimeException | LinkageError ex) {
            throw new StoneException("Could not run the Steam native worker.", ex);
        }
    }

    static Response run(Request request) {
        boolean initialized = false;
        SteamUser user = null;
        SteamUtils utils = null;
        SteamUserStats stats = null;
        try {
            var loader = new SteamNativeLibraries();
            SteamAPI.loadLibraries(loader);
            var result = SteamAPI.initEx();
            if (result != SteamAPI.InitResult.OK)
                return new Response(request.appId(), request.steamId(), Status.REJECTED,
                        "Steam initialization failed (" + result +
                                "). Keep Steam running and check this game's local Steam availability.",
                        java.util.Map.of());
            initialized = true;
            user = new SteamUser(new SteamUserCallback() {
            });
            utils = new SteamUtils(new SteamUtilsCallback() {
            });
            String actualUser = Long.toUnsignedString(SteamNativeHandle.getNativeHandle(user.getSteamID()));
            if (Integer.toUnsignedLong(utils.getAppID()) != request.appId() || !actualUser.equals(request.steamId())) {
                return new Response(request.appId(), request.steamId(), Status.REJECTED,
                        "The running Steam account or game does not match this editor. No changes were applied.",
                        java.util.Map.of());
            }
            var callback = new StorageCallback(request.appId());
            stats = new SteamUserStats(callback);
            // Since SDK 1.61, Steam preloads current-user stats at initialization.
            try (var attributes = new SteamAttributes(loader.path("steam_api"))) {
                long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
                while (stats.getNumAchievements() == 0 && System.nanoTime() < deadline) {
                    SteamAPI.runCallbacks();
                    Thread.sleep(50);
                }
                // Drain initial events before a possible StoreStats call.
                SteamAPI.runCallbacks();
                final SteamUserStats activeStats = stats;
                return new AchievementCommit().execute(request, new AchievementCommit.Stats() {
                    @Override
                    public Entry read(String id) {
                        return attributes.read(id);
                    }

                    @Override
                    public boolean set(String id, boolean value) {
                        return value ? activeStats.setAchievement(id) : activeStats.clearAchievement(id);
                    }

                    @Override
                    public boolean storeAndWait() {
                        callback.begin();
                        try {
                            if (!activeStats.storeStats())
                                return false;
                            long until = System.nanoTime() + Duration.ofSeconds(25).toNanos();
                            while (callback.result() == null && System.nanoTime() < until) {
                                SteamAPI.runCallbacks();
                                Thread.sleep(50);
                            }
                            if (callback.result() == null)
                                throw new StoneException("Storage callback timed out.");
                            return callback.result() == SteamResult.OK;
                        } catch (InterruptedException ex) {
                            throw new StoneException("Waiting for Steam storage was interrupted. Changes may still sync.", ex);
                        } finally {
                            callback.waiting = false;
                        }
                    }
                });
            }
        } catch (InterruptedException ex) {
            throw new StoneException("The Steam session was interrupted. Changes may still sync.", ex);
        } catch (Exception | LinkageError ex) {
            throw new StoneException("Could not complete the Steam session. Changes may still sync.", ex);
        } finally {
            try {
                if (stats != null)
                    stats.dispose();
            } finally {
                try {
                    if (utils != null)
                        utils.dispose();
                } finally {
                    try {
                        if (user != null)
                            user.dispose();
                    } finally {
                        if (initialized)
                            SteamAPI.shutdown();
                    }
                }
            }
        }
    }

    static final class StorageCallback implements SteamUserStatsCallback {
        private final long appId;
        private boolean waiting;
        private SteamResult result;

        StorageCallback(long appId) {
            this.appId = appId;
        }

        void begin() {
            result = null;
            waiting = true;
        }

        SteamResult result() {
            return result;
        }

        @Override
        public void onUserStatsStored(long gameId, SteamResult result) {
            if (waiting && this.result == null && gameId == appId)
                this.result = result;
        }
    }
}
