package steamstone.steam;

import java.time.Duration;

/**
 * Shared identifiers, validation limits, and connection defaults for Steam services.
 */
public final class SteamConstants {
    public static final String API_BASE_URI = "https://api.steampowered.com/";
    public static final Duration API_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final String STEAM_ID_PATTERN = "[0-9]{17}";
    public static final String WEB_API_KEY_PATTERN = "[a-fA-F0-9]{32}";
    public static final long MAX_APP_ID = 0xffffffffL;

}
