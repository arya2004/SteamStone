package steamstone.steam.nativeapi.worker;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUserStats;

import java.time.Duration;

import steamstone.exception.StoneException;
import steamstone.steam.nativeapi.commit.AchievementStats;
import steamstone.steam.nativeapi.protocol.Entry;

public final class NativeAchievementStats implements AchievementStats {
    private final SteamUserStats stats;
    private final SteamAttributes attributes;
    private final StorageCallback callback;

    public NativeAchievementStats(final SteamUserStats stats, final SteamAttributes attributes,
                                  final StorageCallback callback) {
        this.stats = stats;
        this.attributes = attributes;
        this.callback = callback;
    }

    @Override
    public Entry read(final String id) {
        return attributes.read(id);
    }

    @Override
    public boolean set(final String id, final boolean unlocked) {
        return unlocked ? stats.setAchievement(id) : stats.clearAchievement(id);
    }

    @Override
    public boolean storeAndWait() {
        callback.begin();
        try {
            if (!stats.storeStats()) {
                return false;
            }
            final var deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
            while (callback.result() == null && System.nanoTime() < deadline) {
                SteamAPI.runCallbacks();
                Thread.sleep(50);
            }
            if (callback.result() == null) {
                throw new StoneException("Storage callback timed out. Changes may still sync; reload before retrying.");
            }
            return callback.result() == SteamResult.OK;
        } catch (final InterruptedException exception) {
            throw new StoneException("Waiting for Steam storage was interrupted. Changes may still sync.", exception);
        } finally {
            callback.end();
        }
    }
}
