package steamstone.steam.nativeapi.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public final class Response {
    private static final String APP_ID = "appId";
    private static final String STEAM_ID = "steamId";
    private static final String STATUS = "status";
    private static final String MESSAGE = "message";
    private static final String ACHIEVEMENTS = "achievements";

    @JsonProperty(APP_ID)
    private final long appId;
    @JsonProperty(STEAM_ID)
    private final String steamId;
    @JsonProperty(STATUS)
    private final Status status;
    @JsonProperty(MESSAGE)
    private final String message;
    @JsonProperty(ACHIEVEMENTS)
    private final Map<String, Entry> achievements;

    @JsonCreator
    public Response(@JsonProperty(APP_ID) final long appId,
                    @JsonProperty(STEAM_ID) final String steamId,
                    @JsonProperty(STATUS) final Status status,
                    @JsonProperty(MESSAGE) final String message,
                    @JsonProperty(ACHIEVEMENTS) final Map<String, Entry> achievements) {
        this.appId = appId;
        this.steamId = steamId;
        this.status = status;
        this.message = message;
        this.achievements = Map.copyOf(achievements);
    }
}
