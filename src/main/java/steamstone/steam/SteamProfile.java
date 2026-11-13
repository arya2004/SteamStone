package steamstone.steam;

import java.net.URI;

import lombok.Value;
import lombok.experimental.Accessors;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

/**
 * Explicit user input: a SteamID64, vanity name, or Steam Community profile URL.
 */
@Value
@Accessors(fluent = true)
public final class SteamProfile {
    private static final String COMMUNITY_HOST = "steamcommunity.com";
    private static final String WWW_COMMUNITY_HOST = "www.steamcommunity.com";
    private static final String COMMUNITY_HOST_PREFIX = COMMUNITY_HOST + "/";
    private static final String WWW_COMMUNITY_HOST_PREFIX = WWW_COMMUNITY_HOST + "/";
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTPS_PREFIX = "https://";
    private static final String SCHEME_SEPARATOR = "://";
    private static final String TRAILING_SLASH_PATTERN = "/+$";
    private static final String PATH_SEPARATOR = "/";
    private static final String EMPTY_VALUE = "";
    private static final String NUMERIC_PROFILE_PATH = "profiles";
    private static final String VANITY_PROFILE_PATH = "id";
    private static final String NUMERIC_INPUT_PATTERN = "[0-9]+";
    private static final String VANITY_NAME_PATTERN = "[A-Za-z0-9_-]{2,64}";
    private static final int DEFAULT_PORT = -1;
    private static final int PROFILE_PATH_PART_COUNT = 3;
    private static final int PROFILE_KIND_INDEX = 1;
    private static final int PROFILE_VALUE_INDEX = 2;

    private final String value;
    private final boolean needsResolution;

    public static SteamProfile parse(final String input) {
        if (input == null || input.isBlank()) {
            throw new StoneException(ErrorCode.INVALID_INPUT);
        }
        final var stripped = input.strip();
        if (stripped.matches(SteamConstants.STEAM_ID_PATTERN)) {
            return new SteamProfile(stripped, false);
        }
        final var value = stripped.startsWith(COMMUNITY_HOST_PREFIX) || stripped.startsWith(WWW_COMMUNITY_HOST_PREFIX)
                ? HTTPS_PREFIX + stripped : stripped;
        if (value.contains(SCHEME_SEPARATOR)) {
            final URI uri;
            try {
                uri = URI.create(value);
            } catch (final IllegalArgumentException e) {
                throw new StoneException(ErrorCode.INVALID_INPUT, e);
            }
            // Never send the API key to a user-provided host.
            if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null ||
                    uri.getPort() != DEFAULT_PORT
                    || !(COMMUNITY_HOST.equalsIgnoreCase(uri.getHost())
                    || WWW_COMMUNITY_HOST.equalsIgnoreCase(uri.getHost()))) {
                throw new StoneException(ErrorCode.INVALID_INPUT);
            }
            final var parts = uri.getPath().replaceAll(TRAILING_SLASH_PATTERN, EMPTY_VALUE).split(PATH_SEPARATOR);
            if (parts.length == PROFILE_PATH_PART_COUNT) {
                if (parts[PROFILE_KIND_INDEX].equals(NUMERIC_PROFILE_PATH) &&
                        parts[PROFILE_VALUE_INDEX].matches(SteamConstants.STEAM_ID_PATTERN)) {
                    return new SteamProfile(parts[PROFILE_VALUE_INDEX], false);
                }
                if (parts[PROFILE_KIND_INDEX].equals(VANITY_PROFILE_PATH) && validVanity(parts[PROFILE_VALUE_INDEX])) {
                    return new SteamProfile(parts[PROFILE_VALUE_INDEX], true);
                }
            }
            throw new StoneException(ErrorCode.INVALID_INPUT);
        }
        if (validVanity(value) && !value.matches(NUMERIC_INPUT_PATTERN)) {
            return new SteamProfile(value, true);
        }
        throw new StoneException(ErrorCode.INVALID_INPUT);
    }

    private static boolean validVanity(final String value) {
        return value.matches(VANITY_NAME_PATTERN);
    }
}
