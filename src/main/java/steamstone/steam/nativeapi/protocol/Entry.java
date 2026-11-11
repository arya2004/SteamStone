package steamstone.steam.nativeapi.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.beans.ConstructorProperties;

import lombok.experimental.Accessors;

import steamstone.model.achievement.AchievementState;

@Value
@Accessors(fluent = true)
@AllArgsConstructor(onConstructor_ = @ConstructorProperties({ProtocolKeys.STATE, ProtocolKeys.DESCRIPTION,
        ProtocolKeys.UNLOCK_TIME}))
public final class Entry {
    @JsonProperty(ProtocolKeys.STATE)
    private final AchievementState state;
    @JsonProperty(ProtocolKeys.DESCRIPTION)
    private final String description;
    @JsonProperty(ProtocolKeys.UNLOCK_TIME)
    private final long unlockTime;

}
