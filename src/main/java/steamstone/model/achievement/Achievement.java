package steamstone.model.achievement;

/**
 * Steam's original account state, separate from any local checkbox preview.
 */
public record Achievement(String id, String name, String description, AchievementState state,
                          long unlockTime, String icon, String lockedIcon) {

}
