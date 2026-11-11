package steamstone.steam;

import steamstone.exception.StoneException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import steamstone.model.Game;
import steamstone.model.Achievement;
import steamstone.model.AchievementList;
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
import java.util.HashMap;
import java.util.Set;

/**
 * Read-only, documented Steam Web API. No global catalogs or internal Steam files.
 */
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

    public SteamLibrary loadLibrary(String apiKey, String profileInput) {
        String key = apiKey == null ? "" : apiKey.strip();
        if (!key.matches("[a-fA-F0-9]{32}")) {
            throw new StoneException("Enter the 32-character Steam Web API key from steamcommunity.com/dev/apikey.");
        }
        SteamProfile profile = SteamProfile.parse(profileInput);

        String steamId = profile.value();
        if (profile.needsResolution()) {
            JsonNode result = get("ISteamUser/ResolveVanityURL/v1/?vanityurl=" + encode(profile.value()), key);
            steamId = result.path("response").path("steamid").asText();
            if (!steamId.matches("[0-9]{17}")) {
                throw new StoneException(
                        "That Steam profile could not be found. Check the URL or use your SteamID64.");
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
                if (!candidate.isBlank())
                    playerName = candidate;
            }
        } catch (StoneException ignored) {
            if (Thread.currentThread().isInterrupted()) {
                throw new StoneException("The Steam request was interrupted.", ignored);
            }
            // Intentionally omit remote response bodies and exceptions from diagnostics.
        }
        return new SteamLibrary(steamId, playerName, library.games(), library.fetchedAt());
    }

    SteamLibrary parseLibrary(JsonNode response, String steamId, String playerName) {
        if (!response.isObject() || !response.path("game_count").canConvertToInt()
                || !response.path("game_count").isIntegralNumber()) {
            throw new StoneException(
                    "Steam did not expose this library. Check the profile and set Game details to Public in Steam " +
                            "privacy settings, then retry.");
        }
        int count = response.path("game_count").asInt();
        JsonNode entries = response.path("games");
        if (count < 0 || (count > 0 && !entries.isArray())) {
            throw new StoneException("Steam returned an incomplete library. Please try again.");
        }
        var games = new ArrayList<Game>();
        Set<Long> seen = new HashSet<>();
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                JsonNode id = entry.path("appid");
                if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0
                        || id.asLong() > 0xffffffffL) {
                    throw new StoneException("Steam returned an invalid game entry. Please retry.");
                }
                long appId = id.asLong();
                if (!seen.add(appId))
                    continue;
                String name = entry.path("name").asText("").strip();
                if (name.isEmpty())
                    name = "App " + appId; // Explicit identifier placeholder, not invented metadata.
                games.add(new Game(appId, name,
                        Math.max(0, entry.path("playtime_forever").asLong()),
                        Math.max(0, entry.path("rtime_last_played").asLong()),
                        entry.path("img_icon_url").asText("")));
            }
        }
        if (games.size() != count) {
            throw new StoneException(
                    "Steam returned a partial library. Refresh to try again; no cached games were added.");
        }
        games.sort(Comparator.comparing(Game::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(Game::appId));
        return new SteamLibrary(steamId, playerName, games, Instant.now());
    }

    public AchievementList loadAchievements(String apiKey, String steamId, long appId) {
        if (apiKey == null || !apiKey.matches("[a-fA-F0-9]{32}") || steamId == null
                || !steamId.matches("[0-9]{17}") || appId <= 0 || appId > 0xffffffffL) {
            throw new StoneException("A valid account, API key, and game are required.");
        }
        JsonNode schema =
                get("ISteamUserStats/GetSchemaForGame/v2/?appid=" + appId + "&l=english", apiKey).path("game");
        if (!schema.isObject() || schema.isEmpty()) {
            throw new StoneException("Steam did not provide achievement definitions for this game.");
        }
        JsonNode definitions = schema.path("availableGameStats").path("achievements");
        if (definitions.isMissingNode() || definitions.isNull() || (definitions.isArray() && definitions.isEmpty())) {
            return new AchievementList(java.util.List.of(), "Steam returned no achievement definitions for this game.");
        }
        if (!definitions.isArray())
            throw new StoneException("Steam returned unexpected achievement definitions.");

        JsonNode player = null;
        String notice = "";
        try {
            player = get("ISteamUserStats/GetPlayerAchievements/v1/?appid=" + appId
                    + "&steamid=" + steamId + "&l=english", apiKey).path("playerstats");
        } catch (StoneException ex) {
            if (Thread.currentThread().isInterrupted()) {
                throw new StoneException("The Steam request was interrupted.", ex);
            }
            notice = ex.getMessage() + " Achievement states are unavailable.";
        }
        return combineAchievements(definitions, player, steamId, notice);
    }

    AchievementList combineAchievements(JsonNode definitions, JsonNode player, String steamId, String notice) {
        var states = new HashMap<String, JsonNode>();
        if (player != null && player.path("success").asBoolean(false)) {
            String returnedId = player.path("steamID").asText(steamId);
            if (!returnedId.equals(steamId))
                throw new StoneException("Steam returned achievement data for a different account.");
            JsonNode entries = player.path("achievements");
            if (entries.isArray()) {
                for (JsonNode entry : entries) {
                    String name = entry.path("apiname").asText();
                    if (!name.isBlank() && states.put(name, entry) != null) {
                        throw new StoneException("Steam returned duplicate achievement states. Please retry.");
                    }
                }
            }
        }
        var result = new ArrayList<Achievement>();
        var names = new HashSet<String>();
        int unknown = 0;
        for (JsonNode definition : definitions) {
            String id = definition.path("name").asText();
            if (id.isBlank() || !names.add(id))
                throw new StoneException("Steam returned invalid achievement identifiers.");
            String name = definition.path("displayName").asText(id);
            if (name.isBlank())
                name = id;
            String description = definition.path("description").asText("");
            JsonNode state = states.get(id);
            if (description.isBlank() && state != null)
                description = state.path("description").asText("");
            Achievement.State status = Achievement.State.UNKNOWN;
            long time = 0;
            if (state != null && state.path("achieved").isIntegralNumber()) {
                int achieved = state.path("achieved").asInt(-1);
                if (achieved == 0)
                    status = Achievement.State.LOCKED;
                if (achieved == 1) {
                    status = Achievement.State.UNLOCKED;
                    time = Math.max(0, state.path("unlocktime").asLong());
                }
            }
            if (status == Achievement.State.UNKNOWN)
                unknown++;
            result.add(new Achievement(id, name, description, status, time,
                    definition.path("icon").asText(""), definition.path("icongray").asText("")));
        }
        if (unknown > 0 && notice.isBlank()) {
            notice = "Steam did not expose the state of " + unknown +
                    " achievement(s). Check Game details visibility; unavailable states are not treated as locked.";
        }
        return new AchievementList(result, notice);
    }

    private JsonNode get(String path, String apiKey) {
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
                throw new StoneException(
                        "Steam rejected the request. Check your API key and profile access, then retry.");
            }
            if (status == 429)
                throw new StoneException("Steam is rate limiting requests. Wait a little before trying again.");
            if (status != 200)
                throw new StoneException("Steam is unavailable (HTTP " + status + "). Please try again later.");
            byte[] bytes = response.body();
            if (bytes.length > MAX_RESPONSE_BYTES)
                throw new StoneException("Steam's response was too large to load safely.");
            try {
                JsonNode value = json.readTree(bytes);
                if (value == null)
                    throw new StoneException("Steam returned an empty response. Please retry.");
                return value;
            } catch (IOException ex) {
                throw new StoneException("Steam returned an unexpected response. Please try again later.", ex);
            }
        } catch (InterruptedException ex) {
            throw new StoneException("The Steam request was interrupted.", ex);
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new StoneException("Steam took too long to respond. Check your connection and retry.", ex);
        } catch (IOException ex) {
            throw new StoneException("Couldn't reach Steam. Check your internet connection and try again.", ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
