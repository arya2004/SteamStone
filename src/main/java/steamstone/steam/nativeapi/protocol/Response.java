package steamstone.steam.nativeapi.protocol;

import java.util.Map;

public record Response(long appId, String steamId, Status status, String message, Map<String, Entry> achievements) {
    public Response(final long appId, final String steamId, final Status status, final String message,
                    final Map<String, Entry> achievements) {
        this.appId = appId;
        this.steamId = steamId;
        this.status = status;
        this.message = message;
        this.achievements = Map.copyOf(achievements);
    }
}
