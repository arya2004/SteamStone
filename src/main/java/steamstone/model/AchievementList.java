package steamstone.model;

import java.util.List;

public record AchievementList(List<Achievement> achievements, String notice) {
    public AchievementList {
        achievements = List.copyOf(achievements);
    }
}
