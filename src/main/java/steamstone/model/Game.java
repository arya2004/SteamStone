package steamstone.model;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public final class Game {
    private final long appId;
    private final String name;
    private final long playtimeMinutes;
    private final long lastPlayed;
    private final String iconHash;

    public String iconUrl() {
        if (iconHash == null || !iconHash.matches("[a-fA-F0-9]{40}"))
            return null;
        return "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/"
                + appId + "/" + iconHash + ".jpg";
    }
}
