package steamstone.steam.nativeapi.worker;

import java.util.List;

/**
 * Names exported by the pinned Steam native API and its callback polling cadence.
 */
public final class SteamNativeConstants {
    public static final String LIBRARY_NAME = "steam_api";
    public static final String USER_STATS_SYMBOL = "SteamAPI_SteamUserStats_v013";
    public static final String ACHIEVEMENT_ATTRIBUTE_SYMBOL = "SteamAPI_ISteamUserStats_GetAchievementDisplayAttribute";
    public static final String ACHIEVEMENT_STATE_SYMBOL = "SteamAPI_ISteamUserStats_GetAchievementAndUnlockTime";
    public static final List<String> REQUIRED_SYMBOLS = List.of(
            USER_STATS_SYMBOL, ACHIEVEMENT_ATTRIBUTE_SYMBOL, ACHIEVEMENT_STATE_SYMBOL);
    public static final long CALLBACK_POLL_INTERVAL_MILLIS = 50;

}
