package steamstone.steam.nativeapi.protocol;

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
    @JsonProperty("appId")
    private final long appId;
    @JsonProperty("steamId")
    private final String steamId;
    @JsonProperty("ids")
    private final List<String> ids;
    @JsonProperty("expected")
    private final Map<String, Boolean> expected;
    @JsonProperty("changes")
    private final Map<String, Boolean> changes;

    @JsonCreator
    public Request(@JsonProperty("appId") final long appId, @JsonProperty("steamId") final String steamId,
                   @JsonProperty("ids") final List<String> ids,
                   @JsonProperty("expected") final Map<String, Boolean> expected,
                   @JsonProperty("changes") final Map<String, Boolean> changes) {
        this.appId = appId;
        this.steamId = steamId;
        try {
            this.ids = List.copyOf(ids);
            this.expected = Map.copyOf(expected);
            this.changes = Map.copyOf(changes);
        } catch (final NullPointerException exception) {
            throw new StoneException("Native request collections must not contain null values.", exception);
        }
        if (appId <= 0 || appId > 0xffffffffL || steamId == null || !steamId.matches("[0-9]{17}")
                || this.ids.size() > 20000 ||
                this.ids.stream().anyMatch((final var id) -> id.isBlank() || id.length() > 256)
                || this.ids.stream().distinct().count() != this.ids.size()
                || !this.ids.containsAll(this.changes.keySet())
                || !this.expected.keySet().containsAll(this.changes.keySet())) {
            throw new StoneException("Invalid native request context.");
        }
    }
}
