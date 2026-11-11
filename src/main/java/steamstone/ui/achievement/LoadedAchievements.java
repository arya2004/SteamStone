package steamstone.ui.achievement;

import steamstone.model.achievement.AchievementList;

public record LoadedAchievements(AchievementList data, boolean nativeReady) {
}
