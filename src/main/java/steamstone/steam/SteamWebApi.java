package steamstone.steam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import steamstone.model.Game;
import steamstone.model.SteamLibrary;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/** Read-only, documented Steam Web API. No global catalogs or internal Steam files. */
public final class SteamWebApi {
    private final HttpClient client;
    private final URI base;
    private final ObjectMapper json = new ObjectMapper();
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    public SteamWebApi() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), URI.create("https://api.steampowered.com/"));
    }

    SteamWebApi(HttpClient client, URI base) {
        this.client = client;
        this.base = base;
    }

    public SteamLibrary loadLibrary(String apiKey, String profileInput)
            throws SteamApiException, InterruptedException {
        String key = apiKey == null ? "" : apiKey.strip();
        if (!key.matches("[a-fA-F0-9]{32}")) {
            throw new SteamApiException("Enter the 32-character Steam Web API key from steamcommunity.com/dev/apikey.");
        }
        SteamProfile profile;
        try {
            profile = SteamProfile.parse(profileInput);
        } catch (IllegalArgumentException ex) {
            throw new SteamApiException(ex.getMessage());
        }

        String steamId = profile.value();
        if (profile.needsResolution()) {
            JsonNode result = get("ISteamUser/ResolveVanityURL/v1/?vanityurl=" + encode(profile.value()), key);
            steamId = result.path("response").path("steamid").asText();
            if (!steamId.matches("[0-9]{17}")) {
                throw new SteamApiException("That Steam profile could not be found. Check the URL or use your SteamID64.");
            }
        }

        String input = "{\"steamid\":\"" + steamId
                + "\",\"include_appinfo\":true,\"include_played_free_games\":true}";
        JsonNode response = get("IPlayerService/GetOwnedGames/v1/?input_json=" + encode(input), key).path("response");
        SteamLibrary library = parseLibrary(response, steamId, steamId);

        // Profile display information is optional: a failed summary must not hide a valid library.
        String playerName = steamId;
        try {
            JsonNode players = get("ISteamUser/GetPlayerSummaries/v2/?steamids=" + steamId, key)
                    .path("response").path("players");
            if (players.isArray() && !players.isEmpty()) {
                String candidate = players.get(0).path("personaname").asText();
                if (!candidate.isBlank()) playerName = candidate;
            }
        } catch (SteamApiException ignored) {
            // Intentionally omit remote response bodies and exceptions from diagnostics.
        }
        return new SteamLibrary(steamId, playerName, library.games(), library.fetchedAt());
    }

    SteamLibrary parseLibrary(JsonNode response, String steamId, String playerName) throws SteamApiException {
        if (!response.isObject() || !response.path("game_count").canConvertToInt()
                || !response.path("game_count").isIntegralNumber()) {
            throw new SteamApiException("Steam did not expose this library. Check the profile and set Game details to Public in Steam privacy settings, then retry.");
        }
        int count = response.path("game_count").asInt();
        JsonNode entries = response.path("games");
        if (count < 0 || (count > 0 && !entries.isArray())) {
            throw new SteamApiException("Steam returned an incomplete library. Please try again.");
        }
        var games = new ArrayList<Game>();
        Set<Long> seen = new HashSet<>();
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                JsonNode id = entry.path("appid");
                if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0
                        || id.asLong() > 0xffffffffL) {
                    throw new SteamApiException("Steam returned an invalid game entry. Please retry.");
                }
                long appId = id.asLong();
                if (!seen.add(appId)) continue;
                String name = entry.path("name").asText("").strip();
                if (name.isEmpty()) name = "App " + appId; // Explicit identifier placeholder, not invented metadata.
                games.add(new Game(appId, name,
                        Math.max(0, entry.path("playtime_forever").asLong()),
                        Math.max(0, entry.path("rtime_last_played").asLong()),
                        entry.path("img_icon_url").asText("")));
            }
        }
        if (games.size() != count) {
            throw new SteamApiException("Steam returned a partial library. Refresh to try again; no cached games were added.");
        }
        games.sort(Comparator.comparing(Game::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(Game::appId));
        return new SteamLibrary(steamId, playerName, games, Instant.now());
    }

    private JsonNode get(String path, String apiKey) throws SteamApiException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(20))
                .header("x-webapi-key", apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", "SteamStone/0.1.0")
                .GET().build();
        try {
            // Complete the body within the request timeout, rather than returning an unbounded stream.
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status == 401 || status == 403) {
                    throw new SteamApiException("Steam rejected the request. Check your API key and profile access, then retry.");
                }
                if (status == 429) throw new SteamApiException("Steam is rate limiting requests. Wait a little before trying again.");
                if (status != 200) throw new SteamApiException("Steam is unavailable (HTTP " + status + "). Please try again later.");
                byte[] bytes = response.body();
                if (bytes.length > MAX_RESPONSE_BYTES) throw new SteamApiException("Steam's response was too large to load safely.");
                try {
                    JsonNode value = json.readTree(bytes);
                    if (value == null) throw new SteamApiException("Steam returned an empty response. Please retry.");
                    return value;
                } catch (IOException ignored) {
                    throw new SteamApiException("Steam returned an unexpected response. Please try again later.");
                }
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new SteamApiException("Steam took too long to respond. Check your connection and retry.");
        } catch (IOException ex) {
            throw new SteamApiException("Couldn't reach Steam. Check your internet connection and try again.");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
