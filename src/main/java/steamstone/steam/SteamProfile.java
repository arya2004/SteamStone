package steamstone.steam;

import java.net.URI;

/** Explicit user input: a SteamID64, vanity name, or Steam Community profile URL. */
public record SteamProfile(String value, boolean needsResolution) {
    public static SteamProfile parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Enter your Steam profile URL or 17-digit SteamID64.");
        }
        String value = input.strip();
        if (value.matches("[0-9]{17}")) return new SteamProfile(value, false);
        if (value.startsWith("steamcommunity.com/") || value.startsWith("www.steamcommunity.com/")) {
            value = "https://" + value;
        }
        if (value.contains("://")) {
            try {
                URI uri = URI.create(value);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                        || uri.getPort() != -1 || !("steamcommunity.com".equalsIgnoreCase(uri.getHost())
                        || "www.steamcommunity.com".equalsIgnoreCase(uri.getHost()))) {
                    throw new IllegalArgumentException();
                }
                String path = uri.getPath().replaceAll("/+$", "");
                String[] parts = path.split("/");
                if (parts.length != 3) throw new IllegalArgumentException();
                if (parts[1].equals("profiles") && parts[2].matches("[0-9]{17}")) {
                    return new SteamProfile(parts[2], false);
                }
                if (parts[1].equals("id") && validVanity(parts[2])) return new SteamProfile(parts[2], true);
            } catch (IllegalArgumentException ignored) {
                // Reject arbitrary URLs; never send the API key to a user-provided host.
            }
            throw new IllegalArgumentException("Use a Steam Community /id/ or /profiles/ URL.");
        }
        if (validVanity(value) && !value.matches("[0-9]+")) return new SteamProfile(value, true);
        throw new IllegalArgumentException("Use your profile URL, vanity name, or 17-digit SteamID64.");
    }

    private static boolean validVanity(String value) {
        return value.matches("[A-Za-z0-9_-]{2,64}");
    }
}
