package steamstone.steam.nativeapi.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.beans.ConstructorProperties;

import lombok.experimental.Accessors;

import steamstone.model.achievement.AchievementState;

@Value
@Accessors(fluent = true)
@AllArgsConstructor(onConstructor_ = @ConstructorProperties({Entry.STATE, Entry.DESCRIPTION,
        Entry.UNLOCK_TIME}))
public final class Entry {
    private static final String STATE = "state";
    private static final String DESCRIPTION = "description";
    private static final String UNLOCK_TIME = "unlockTime";

    @JsonProperty(STATE)
    private final AchievementState state;
    @JsonProperty(DESCRIPTION)
    private final String description;
    @JsonProperty(UNLOCK_TIME)
    private final long unlockTime;

}
