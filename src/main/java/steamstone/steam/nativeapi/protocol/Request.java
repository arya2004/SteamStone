package steamstone.steam.nativeapi.protocol;

import steamstone.steam.SteamConstants;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

import lombok.Value;
import lombok.experimental.Accessors;

import steamstone.exception.StoneException;

/**
 * Immutable, account-scoped edits; no Web API credentials cross this boundary.
 */
@Value
@Accessors(fluent = true)
public final class Request {
    private static final int MAX_ACHIEVEMENT_COUNT = 20_000;
    private static final int MAX_ACHIEVEMENT_ID_LENGTH = 256;

    @JsonProperty(ProtocolKeys.APP_ID)
    private final long appId;
    @JsonProperty(ProtocolKeys.STEAM_ID)
    private final String steamId;
    @JsonProperty(ProtocolKeys.IDS)
    private final List<String> ids;
    @JsonProperty(ProtocolKeys.EXPECTED)
    private final Map<String, Boolean> expected;
    @JsonProperty(ProtocolKeys.CHANGES)
    private final Map<String, Boolean> changes;

    @JsonCreator
    public Request(@JsonProperty(ProtocolKeys.APP_ID) final long appId,
                   @JsonProperty(ProtocolKeys.STEAM_ID) final String steamId,
                   @JsonProperty(ProtocolKeys.IDS) final List<String> ids,
                   @JsonProperty(ProtocolKeys.EXPECTED) final Map<String, Boolean> expected,
                   @JsonProperty(ProtocolKeys.CHANGES) final Map<String, Boolean> changes) {
        this.appId = appId;
        this.steamId = steamId;
        try {
            this.ids = List.copyOf(ids);
            this.expected = Map.copyOf(expected);
            this.changes = Map.copyOf(changes);
        } catch (final NullPointerException exception) {
            throw new StoneException("Native request collections must not contain null values.", exception);
        }
        if (appId <= 0 || appId > SteamConstants.MAX_APP_ID || steamId == null ||
                !steamId.matches(SteamConstants.STEAM_ID_PATTERN)
                || this.ids.size() > MAX_ACHIEVEMENT_COUNT ||
                this.ids.stream().anyMatch((final var id) -> id.isBlank() || id.length() > MAX_ACHIEVEMENT_ID_LENGTH)
                || this.ids.stream().distinct().count() != this.ids.size()
                || !this.ids.containsAll(this.changes.keySet())
                || !this.expected.keySet().containsAll(this.changes.keySet())) {
            throw new StoneException("Invalid native request context.");
        }
    }
}
