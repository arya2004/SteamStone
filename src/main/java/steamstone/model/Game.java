package steamstone.model;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public final class Game {
    private static final String ICON_HASH_PATTERN = "[a-fA-F0-9]{40}";
    private static final String ICON_BASE_URI =
            "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/";
    private static final String PATH_SEPARATOR = "/";
    private static final String ICON_EXTENSION = ".jpg";

    private final long appId;
    private final String name;
    private final long playtimeMinutes;
    private final long lastPlayed;
    private final String iconHash;

    public String iconUrl() {
        if (iconHash == null || !iconHash.matches(ICON_HASH_PATTERN))
            return null;
        return ICON_BASE_URI
                + appId + PATH_SEPARATOR + iconHash + ICON_EXTENSION;
    }
}
