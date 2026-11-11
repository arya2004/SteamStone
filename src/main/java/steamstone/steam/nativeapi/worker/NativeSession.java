package steamstone.steam.nativeapi.worker;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamUser;
import com.codedisaster.steamworks.SteamUserStats;
import com.codedisaster.steamworks.SteamUtils;
import steamstone.exception.StoneException;

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
        } catch (final RuntimeException | LinkageError exception) {
            try (this) {
                throw new StoneException("Could not create the native Steam session.", exception);
            }
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
        try {
            if (stats != null) {
                stats.dispose();
            }
        } finally {
            try {
                if (utils != null) {
                    utils.dispose();
                }
            } finally {
                try {
                    if (user != null) {
                        user.dispose();
                    }
                } finally {
                    SteamAPI.shutdown();
                }
            }
        }
    }
}
