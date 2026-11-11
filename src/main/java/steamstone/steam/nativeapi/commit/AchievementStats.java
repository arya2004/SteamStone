package steamstone.steam.nativeapi.commit;

import steamstone.steam.nativeapi.protocol.Entry;

public interface AchievementStats {
    Entry read(String id);

    boolean set(String id, boolean unlocked);

    boolean storeAndWait();
}
