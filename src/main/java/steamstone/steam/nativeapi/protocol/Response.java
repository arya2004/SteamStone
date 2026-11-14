package steamstone.steam.nativeapi.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import lombok.Value;
import lombok.experimental.Accessors;
import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

@Value
@Accessors(fluent = true)
public final class Response {
    private static final String APP_ID = "appId";
    private static final String STEAM_ID = "steamId";
    private static final String STATUS = "status";
    private static final String MESSAGE = "message";
    private static final String ERROR_CODE = "errorCode";
    private static final String ACHIEVEMENTS = "achievements";

    @JsonProperty(APP_ID)
    private final long appId;
    @JsonProperty(STEAM_ID)
    private final String steamId;
    @JsonProperty(STATUS)
    private final Status status;
    @JsonProperty(ERROR_CODE)
    private final ErrorCode errorCode;
    @JsonProperty(ACHIEVEMENTS)
    private final Map<String, Entry> achievements;

    @JsonCreator
    public Response(@JsonProperty(APP_ID) final long appId,
                    @JsonProperty(STEAM_ID) final String steamId,
                    @JsonProperty(STATUS) final Status status,
                    @JsonProperty(ERROR_CODE) final ErrorCode errorCode,
                    @JsonProperty(ACHIEVEMENTS) final Map<String, Entry> achievements) {
        if (!hasConsistentOutcome(status, errorCode)) {
            throw new StoneException(ErrorCode.STEAM_SESSION);
        }
        this.appId = appId;
        this.steamId = steamId;
        this.status = status;
        this.errorCode = errorCode;
        this.achievements = Map.copyOf(achievements);
    }

    @JsonProperty(value = MESSAGE, access = JsonProperty.Access.READ_ONLY)
    public String message() {
        return switch (status) {
            case CONFIRMED -> "Changes saved to Steam.";
            case PARTIAL -> "Steam did not confirm every change. Review the remaining preview before retrying.";
            case CONFLICT -> "Steam's achievement state changed. Nothing was sent; review the refreshed preview.";
            default -> errorCode.message();
        };
    }

    private static boolean hasConsistentOutcome(final Status status, final ErrorCode errorCode) {
        return status != null && errorCode != null
                && ((status == Status.READ || status == Status.CONFIRMED) == (errorCode == ErrorCode.NONE));
    }
}
