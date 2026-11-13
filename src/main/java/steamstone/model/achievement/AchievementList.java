package steamstone.model.achievement;

import java.util.List;

import lombok.Value;
import lombok.experimental.Accessors;
import steamstone.exception.ErrorCode;

@Value
@Accessors(fluent = true)
public final class AchievementList {
    private final List<Achievement> achievements;
    private final ErrorCode errorCode;

    public AchievementList(final List<Achievement> achievements, final ErrorCode errorCode) {
        this.achievements = List.copyOf(achievements);
        this.errorCode = errorCode;
    }

    public String notice() {
        return errorCode.message();
    }
}
