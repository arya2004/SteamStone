package steamstone.steam.nativeapi.worker;

import steamstone.exception.ErrorCode;
import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamUser;
import com.codedisaster.steamworks.SteamUserStats;
import com.codedisaster.steamworks.SteamUtils;
import steamstone.exception.ResourceCleanup;

/**
 * Owns the native handles and releases them before shutting Steam down.
 */
public final class NativeSession implements AutoCloseable {
    private SteamUser user;
    private SteamUtils utils;
    private SteamUserStats stats;
    private final StorageCallback callback;

    public NativeSession(final long appId) {
        callback = new StorageCallback(appId);
        try {
            user = new SteamUser(new SteamUserEvents());
            utils = new SteamUtils(new SteamUtilsEvents());
            stats = new SteamUserStats(callback);
        } catch (final RuntimeException | LinkageError e) {
            throw ResourceCleanup.afterFailure(ErrorCode.STEAM_SESSION, e, this::close);
        }
    }

    public SteamUser user() {
        return user;
    }

    public SteamUtils utils() {
        return utils;
    }

    public SteamUserStats stats() {
        return stats;
    }

    public StorageCallback callback() {
        return callback;
    }

    @Override
    public void close() {
        ResourceCleanup.run(
                () -> {
                    if (stats != null)
                        stats.dispose();
                },
                () -> {
                    if (utils != null)
                        utils.dispose();
                },
                () -> {
                    if (user != null)
                        user.dispose();
                },
                SteamAPI::shutdown);
    }
}
