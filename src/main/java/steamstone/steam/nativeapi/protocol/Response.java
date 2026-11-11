package steamstone.steam.nativeapi.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public final class Response {
    @JsonProperty("appId")
    private final long appId;
    @JsonProperty("steamId")
    private final String steamId;
    @JsonProperty("status")
    private final Status status;
    @JsonProperty("message")
    private final String message;
    @JsonProperty("achievements")
    private final Map<String, Entry> achievements;

    @JsonCreator
    public Response(@JsonProperty("appId") final long appId, @JsonProperty("steamId") final String steamId,
                    @JsonProperty("status") final Status status, @JsonProperty("message") final String message,
                    @JsonProperty("achievements") final Map<String, Entry> achievements) {
        this.appId = appId;
        this.steamId = steamId;
        this.status = status;
        this.message = message;
        this.achievements = Map.copyOf(achievements);
    }
}
