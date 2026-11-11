package steamstone.steam.nativeapi.protocol;

import java.util.List;
import java.util.Map;

import steamstone.exception.StoneException;

/**
 * Immutable, account-scoped edits; no Web API credentials cross this boundary.
 */
public record Request(long appId, String steamId, List<String> ids,
                      Map<String, Boolean> expected, Map<String, Boolean> changes) {
    public Request(final long appId, final String steamId, final List<String> ids,
                   final Map<String, Boolean> expected, final Map<String, Boolean> changes) {
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
