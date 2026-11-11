package steamstone.steam.nativeapi.commit;

import steamstone.steam.nativeapi.protocol.Entry;

public interface AchievementStats {
    public Entry read(final String id);

    public boolean set(final String id, final boolean unlocked);

    public boolean storeAndWait();
}
