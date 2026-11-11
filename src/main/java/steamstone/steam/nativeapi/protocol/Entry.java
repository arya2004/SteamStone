package steamstone.steam.nativeapi.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.beans.ConstructorProperties;

import lombok.experimental.Accessors;

import steamstone.model.achievement.AchievementState;

@Value
@Accessors(fluent = true)
@AllArgsConstructor(onConstructor_ = @ConstructorProperties({"state", "description", "unlockTime"}))
public final class Entry {
    @JsonProperty("state")
    private final AchievementState state;
    @JsonProperty("description")
    private final String description;
    @JsonProperty("unlockTime")
    private final long unlockTime;

}
