package steamstone.steam.nativeapi.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public final class Response {
    @JsonProperty(ProtocolKeys.APP_ID)
    private final long appId;
    @JsonProperty(ProtocolKeys.STEAM_ID)
    private final String steamId;
    @JsonProperty(ProtocolKeys.STATUS)
    private final Status status;
    @JsonProperty(ProtocolKeys.MESSAGE)
    private final String message;
    @JsonProperty(ProtocolKeys.ACHIEVEMENTS)
    private final Map<String, Entry> achievements;

    @JsonCreator
    public Response(@JsonProperty(ProtocolKeys.APP_ID) final long appId,
                    @JsonProperty(ProtocolKeys.STEAM_ID) final String steamId,
                    @JsonProperty(ProtocolKeys.STATUS) final Status status,
                    @JsonProperty(ProtocolKeys.MESSAGE) final String message,
                    @JsonProperty(ProtocolKeys.ACHIEVEMENTS) final Map<String, Entry> achievements) {
        this.appId = appId;
        this.steamId = steamId;
        this.status = status;
        this.message = message;
        this.achievements = Map.copyOf(achievements);
    }
}
