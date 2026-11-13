package steamstone.steam.nativeapi.protocol;

import steamstone.steam.SteamConstants;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

import lombok.Value;
import lombok.experimental.Accessors;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

/**
 * Immutable, account-scoped edits; no Web API credentials cross this boundary.
 */
@Value
@Accessors(fluent = true)
public final class Request {
    private static final long MAX_APP_ID = 0xffffffffL;
    private static final String APP_ID = "appId";
    private static final String STEAM_ID = "steamId";
    private static final String IDS = "ids";
    private static final String EXPECTED = "expected";
    private static final String CHANGES = "changes";

    private static final int MAX_ACHIEVEMENT_COUNT = 20_000;
    private static final int MAX_ACHIEVEMENT_ID_LENGTH = 256;

    @JsonProperty(APP_ID)
    private final long appId;
    @JsonProperty(STEAM_ID)
    private final String steamId;
    @JsonProperty(IDS)
    private final List<String> ids;
    @JsonProperty(EXPECTED)
    private final Map<String, Boolean> expected;
    @JsonProperty(CHANGES)
    private final Map<String, Boolean> changes;

    @JsonCreator
    public Request(@JsonProperty(APP_ID) final long appId,
                   @JsonProperty(STEAM_ID) final String steamId,
                   @JsonProperty(IDS) final List<String> ids,
                   @JsonProperty(EXPECTED) final Map<String, Boolean> expected,
                   @JsonProperty(CHANGES) final Map<String, Boolean> changes) {
        this.appId = appId;
        this.steamId = steamId;
        try {
            this.ids = List.copyOf(ids);
            this.expected = Map.copyOf(expected);
            this.changes = Map.copyOf(changes);
        } catch (final NullPointerException e) {
            throw new StoneException(ErrorCode.INVALID_INPUT, e);
        }
        if (appId <= 0 || appId > MAX_APP_ID || steamId == null ||
                !steamId.matches(SteamConstants.STEAM_ID_PATTERN)
                || this.ids.size() > MAX_ACHIEVEMENT_COUNT ||
                this.ids.stream().anyMatch((final var id) -> id.isBlank() || id.length() > MAX_ACHIEVEMENT_ID_LENGTH)
                || this.ids.stream().distinct().count() != this.ids.size()
                || !this.ids.containsAll(this.changes.keySet())
                || !this.expected.keySet().containsAll(this.changes.keySet())) {
            throw new StoneException(ErrorCode.INVALID_INPUT);
        }
    }
}
