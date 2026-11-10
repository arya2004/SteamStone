package steamstone.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local-only edits; this class cannot call Steam or persist changes. */
public final class AchievementPreview {
    private final List<Achievement> achievements;
    private final Map<String, Boolean> changes = new LinkedHashMap<>();

    public AchievementPreview(List<Achievement> achievements) {
        this.achievements = List.copyOf(achievements);
    }

    public List<Achievement> achievements() { return achievements; }
    public int changeCount() { return changes.size(); }
    public boolean isChanged(Achievement achievement) { return changes.containsKey(achievement.id()); }
    public boolean isUnlocked(Achievement achievement) {
        return changes.getOrDefault(achievement.id(), achievement.state() == Achievement.State.UNLOCKED);
    }

    public void setUnlocked(Achievement achievement, boolean unlocked) {
        if (achievement.state() == Achievement.State.UNKNOWN || !achievements.contains(achievement)) return;
        if (unlocked == (achievement.state() == Achievement.State.UNLOCKED)) changes.remove(achievement.id());
        else changes.put(achievement.id(), unlocked);
    }

    public void setAll(boolean unlocked) {
        for (Achievement achievement : achievements) setUnlocked(achievement, unlocked);
    }

    public void reset() { changes.clear(); }
}
