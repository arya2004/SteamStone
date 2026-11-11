package steamstone.model.achievement;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Steam's original account state, separate from any local checkbox preview.
 */
@Value
@Accessors(fluent = true)
public final class Achievement {
    private final String id;
    private final String name;
    private final String description;
    private final AchievementState state;
    private final long unlockTime;
    private final String icon;
    private final String lockedIcon;

}
