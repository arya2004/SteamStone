package steamstone.steam.nativeapi;

import steamstone.exception.StoneException;
import steamstone.model.Achievement;

import java.util.List;
import java.util.Map;

/**
 * Small file-based protocol. No Web API credentials cross this boundary.
 */
public final class NativeProtocol {
    private NativeProtocol() {
    }

    public record Request(long appId, String steamId, List<String> ids,
                          Map<String, Boolean> expected, Map<String, Boolean> changes) {
        public Request {
            try {
                ids = List.copyOf(ids);
                expected = Map.copyOf(expected);
                changes = Map.copyOf(changes);
            } catch (NullPointerException ex) {
                throw new StoneException("Native request collections must not contain null values.", ex);
            }
            if (appId <= 0 || appId > 0xffffffffL || steamId == null || !steamId.matches("[0-9]{17}")
                    || ids.size() > 20000 || ids.stream().anyMatch(id -> id.isBlank() || id.length() > 256)
                    || ids.stream().distinct().count() != ids.size()
                    || !ids.containsAll(changes.keySet()) || !expected.keySet().containsAll(changes.keySet())) {
                throw new StoneException("Invalid native request context.");
            }
        }
    }

    public record Entry(Achievement.State state, String description, long unlockTime) {
    }

    public enum Status {READ, CONFIRMED, PARTIAL, REJECTED, CONFLICT, UNKNOWN}

    public record Response(long appId, String steamId, Status status, String message, Map<String, Entry> achievements) {
        public Response {
            achievements = Map.copyOf(achievements);
        }
    }
}
