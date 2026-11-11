package steamstone.steam.nativeapi.protocol;

import steamstone.model.achievement.AchievementState;

public record Entry(AchievementState state, String description, long unlockTime) {
}
