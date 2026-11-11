package steamstone.model.achievement;

import java.util.List;

public record AchievementList(List<Achievement> achievements, String notice) {
    public AchievementList(final List<Achievement> achievements, final String notice) {
        this.achievements = List.copyOf(achievements);
        this.notice = notice;
    }
}
