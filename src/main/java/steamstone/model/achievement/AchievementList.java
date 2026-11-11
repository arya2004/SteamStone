package steamstone.model.achievement;

import java.util.List;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public final class AchievementList {
    private final List<Achievement> achievements;
    private final String notice;

    public AchievementList(final List<Achievement> achievements, final String notice) {
        this.achievements = List.copyOf(achievements);
        this.notice = notice;
    }
}
