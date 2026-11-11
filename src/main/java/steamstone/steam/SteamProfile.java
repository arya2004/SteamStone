package steamstone.steam;

import java.net.URI;

import lombok.Value;
import lombok.experimental.Accessors;

import steamstone.exception.StoneException;

/**
 * Explicit user input: a SteamID64, vanity name, or Steam Community profile URL.
 */
@Value
@Accessors(fluent = true)
public final class SteamProfile {
    private final String value;
    private final boolean needsResolution;

    public static SteamProfile parse(final String input) {
        if (input == null || input.isBlank()) {
            throw new StoneException("Enter your Steam profile URL or 17-digit SteamID64.");
        }
        final var stripped = input.strip();
        if (stripped.matches("[0-9]{17}")) {
            return new SteamProfile(stripped, false);
        }
        final var value = stripped.startsWith("steamcommunity.com/") || stripped.startsWith("www.steamcommunity.com/")
                ? "https://" + stripped : stripped;
        if (value.contains("://")) {
            final URI uri;
            try {
                uri = URI.create(value);
            } catch (final IllegalArgumentException exception) {
                throw new StoneException("Use a Steam Community /id/ or /profiles/ URL.", exception);
            }
            // Never send the API key to a user-provided host.
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getPort() != -1
                    || !("steamcommunity.com".equalsIgnoreCase(uri.getHost())
                    || "www.steamcommunity.com".equalsIgnoreCase(uri.getHost()))) {
                throw new StoneException("Use a Steam Community /id/ or /profiles/ URL.");
            }
            final var parts = uri.getPath().replaceAll("/+$", "").split("/");
            if (parts.length == 3) {
                if (parts[1].equals("profiles") && parts[2].matches("[0-9]{17}")) {
                    return new SteamProfile(parts[2], false);
                }
                if (parts[1].equals("id") && validVanity(parts[2])) {
                    return new SteamProfile(parts[2], true);
                }
            }
            throw new StoneException("Use a Steam Community /id/ or /profiles/ URL.");
        }
        if (validVanity(value) && !value.matches("[0-9]+")) {
            return new SteamProfile(value, true);
        }
        throw new StoneException("Use your profile URL, vanity name, or 17-digit SteamID64.");
    }

    private static boolean validVanity(final String value) {
        return value.matches("[A-Za-z0-9_-]{2,64}");
    }
}
