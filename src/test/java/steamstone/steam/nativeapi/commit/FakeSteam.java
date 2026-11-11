package steamstone.steam.nativeapi.commit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import steamstone.exception.StoneException;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.protocol.Entry;

final class FakeSteam implements AchievementStats {
    Map<String, AchievementState> states =
            new HashMap<>(Map.of("A", AchievementState.LOCKED, "B", AchievementState.UNLOCKED));
    Set<String> reject = new HashSet<>();
    boolean storageAccepted = true, timeout, ignoreSetters;
    int setters, stores;

    @Override
    public Entry read(String id) {
        return new Entry(states.get(id), "Description", 0);
    }

    @Override
    public boolean set(String id, boolean unlocked) {
        setters++;
        if (reject.contains(id))
            return false;
        if (!ignoreSetters)
            states.put(id, unlocked ? AchievementState.UNLOCKED : AchievementState.LOCKED);
        return true;
    }

    @Override
    public boolean storeAndWait() {
        stores++;
        if (timeout)
            throw new StoneException("Storage timed out.", new TimeoutException());
        return storageAccepted;
    }
}
