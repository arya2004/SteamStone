package steamstone.steam.nativeapi.protocol;

import java.util.Map;

public record Response(long appId, String steamId, Status status, String message, Map<String, Entry> achievements) {
    public Response {
        achievements = Map.copyOf(achievements);
    }
}
