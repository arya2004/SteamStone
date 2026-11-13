package steamstone.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.beans.ConstructorProperties;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.ToString;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@AllArgsConstructor(onConstructor_ = @ConstructorProperties({SavedConnection.API_KEY, SavedConnection.PROFILE}))
public final class SavedConnection {
    private static final String API_KEY = "apiKey";
    private static final String PROFILE = "profile";
    @ToString.Exclude
    @JsonProperty(API_KEY)
    private final String apiKey;
    @JsonProperty(PROFILE)
    private final String profile;
}
