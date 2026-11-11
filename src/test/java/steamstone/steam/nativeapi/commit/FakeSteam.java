package steamstone.steam.nativeapi.commit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import steamstone.exception.StoneException;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.protocol.Entry;

public final class FakeSteam implements AchievementStats {
    public final Map<String, AchievementState> states =
            new HashMap<>(Map.of("A", AchievementState.LOCKED, "B", AchievementState.UNLOCKED));
    public final Set<String> reject = new HashSet<>();
    public boolean storageAccepted = true, timeout, ignoreSetters;
    public int setters, stores;

    @Override
    public Entry read(final String id) {
        return new Entry(states.get(id), "Description", 0);
    }

    @Override
    public boolean set(final String id, final boolean unlocked) {
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
