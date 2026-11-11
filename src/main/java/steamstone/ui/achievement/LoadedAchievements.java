package steamstone.ui.achievement;

import lombok.Value;
import lombok.experimental.Accessors;

import steamstone.model.achievement.AchievementList;

@Value
@Accessors(fluent = true)
public final class LoadedAchievements {
    private final AchievementList data;
    private final boolean nativeReady;

}
