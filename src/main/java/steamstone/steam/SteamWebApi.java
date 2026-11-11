package steamstone.steam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import steamstone.concurrent.StoneFutures;
import steamstone.exception.StoneException;
import steamstone.model.Game;
import steamstone.model.SteamLibrary;
import steamstone.model.achievement.Achievement;
import steamstone.model.achievement.AchievementList;
import steamstone.model.achievement.AchievementState;

/**
 * Read-only, documented Steam Web API. No global catalogs or internal Steam files.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class SteamWebApi {
    private final HttpClient client;
    private final URI base;
    private final ObjectMapper json = new ObjectMapper();
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    public SteamWebApi() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), URI.create("https://api.steampowered.com/"));
    }

    public SteamLibrary loadLibrary(final String apiKey, final String profileInput) {
        final var key = apiKey == null ? "" : apiKey.strip();
        if (!key.matches("[a-fA-F0-9]{32}")) {
            throw new StoneException("Enter the 32-character Steam Web API key from steamcommunity.com/dev/apikey.");
        }
        final var profile = SteamProfile.parse(profileInput);
        final var steamId = profile.needsResolution() ? resolveProfile(profile, key) : profile.value();

        final var input = "{\"steamid\":\"" + steamId
                + "\",\"include_appinfo\":true,\"include_played_free_games\":true}";
        final var response = get("IPlayerService/GetOwnedGames/v1/?input_json=" + encode(input), key).path("response");
        final var library = parseLibrary(response, steamId, steamId);

        // Optional display data must not hide a valid library snapshot.
        final var playerName = StoneFutures.await(CompletableFuture.completedFuture(steamId)
                .thenApply((final var id) -> get("ISteamUser/GetPlayerSummaries/v2/?steamids=" + id, key)
                        .path("response").path("players"))
                .handle((final var players, final var failure) -> {
                    requireNotInterrupted();
                    if (failure != null || !players.isArray() || players.isEmpty()) {
                        return steamId;
                    }
                    final var candidate = players.get(0).path("personaname").asText();
                    return candidate.isBlank() ? steamId : candidate;
                }));
        return new SteamLibrary(steamId, playerName, library.games(), library.fetchedAt());
    }

    private String resolveProfile(final SteamProfile profile, final String key) {
        final var result = get("ISteamUser/ResolveVanityURL/v1/?vanityurl=" + encode(profile.value()), key);
        final var steamId = result.path("response").path("steamid").asText();
        if (!steamId.matches("[0-9]{17}")) {
            throw new StoneException("That Steam profile could not be found. Check the URL or use your SteamID64.");
        }
        return steamId;
    }

    public SteamLibrary parseLibrary(final JsonNode response, final String steamId, final String playerName) {
        if (!response.isObject() || !response.path("game_count").canConvertToInt()
                || !response.path("game_count").isIntegralNumber()) {
            throw new StoneException(
                    "Steam did not expose this library. Check the profile and set Game details to Public in Steam " +
                            "privacy settings, then retry.");
        }
        final var count = response.path("game_count").asInt();
        final var entries = response.path("games");
        if (count < 0 || (count > 0 && !entries.isArray())) {
            throw new StoneException("Steam returned an incomplete library. Please try again.");
        }
        final var games = new ArrayList<Game>();
        final var seen = new HashSet<Long>();
        if (entries.isArray()) {
            for (final var entry : entries) {
                final var id = entry.path("appid");
                if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0
                        || id.asLong() > 0xffffffffL) {
                    throw new StoneException("Steam returned an invalid game entry. Please retry.");
                }
                final var appId = id.asLong();
                if (!seen.add(appId))
                    continue;
                final var suppliedName = entry.path("name").asText("").strip();
                final var name = suppliedName.isEmpty() ? "App " + appId : suppliedName;
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

    public AchievementList loadAchievements(final String apiKey, final String steamId, final long appId) {
        if (apiKey == null || !apiKey.matches("[a-fA-F0-9]{32}") || steamId == null
                || !steamId.matches("[0-9]{17}") || appId <= 0 || appId > 0xffffffffL) {
            throw new StoneException("A valid account, API key, and game are required.");
        }
        final var schema =
                get("ISteamUserStats/GetSchemaForGame/v2/?appid=" + appId + "&l=english", apiKey).path("game");
        if (!schema.isObject() || schema.isEmpty()) {
            throw new StoneException("Steam did not provide achievement definitions for this game.");
        }
        final var definitions = schema.path("availableGameStats").path("achievements");
        if (definitions.isMissingNode() || definitions.isNull() || (definitions.isArray() && definitions.isEmpty())) {
            return new AchievementList(List.of(), "Steam returned no achievement definitions for this game.");
        }
        if (!definitions.isArray())
            throw new StoneException("Steam returned unexpected achievement definitions.");

        final var snapshot = StoneFutures.await(CompletableFuture.completedFuture(steamId)
                .thenApply((final var id) -> get("ISteamUserStats/GetPlayerAchievements/v1/?appid=" + appId
                        + "&steamid=" + id + "&l=english", apiKey).path("playerstats"))
                .handle((final var player, final var failure) -> {
                    requireNotInterrupted();
                    return failure == null ? new PlayerSnapshot(player, "")
                            : new PlayerSnapshot(null, "Achievement states are unavailable. Check your profile "
                            + "visibility and reload before editing.");
                }));
        return combineAchievements(definitions, snapshot.player(), steamId, snapshot.notice());
    }

    public AchievementList combineAchievements(final JsonNode definitions, final JsonNode player, final String steamId,
                                               final String notice) {
        final var states = new HashMap<String, JsonNode>();
        if (player != null && player.path("success").asBoolean(false)) {
            final var returnedId = player.path("steamID").asText(steamId);
            if (!returnedId.equals(steamId))
                throw new StoneException("Steam returned achievement data for a different account.");
            final var entries = player.path("achievements");
            if (entries.isArray()) {
                for (final var entry : entries) {
                    final var name = entry.path("apiname").asText();
                    if (!name.isBlank() && states.put(name, entry) != null) {
                        throw new StoneException("Steam returned duplicate achievement states. Please retry.");
                    }
                }
            }
        }
        final var result = new ArrayList<Achievement>();
        final var names = new HashSet<String>();
        for (final var definition : definitions) {
            final var id = definition.path("name").asText();
            if (id.isBlank() || !names.add(id))
                throw new StoneException("Steam returned invalid achievement identifiers.");
            final var suppliedName = definition.path("displayName").asText(id);
            final var name = suppliedName.isBlank() ? id : suppliedName;
            final var state = states.get(id);
            final var suppliedDescription = definition.path("description").asText("");
            final var description = suppliedDescription.isBlank() && state != null
                    ? state.path("description").asText("") : suppliedDescription;
            final var achieved = state != null && state.path("achieved").isIntegralNumber()
                    ? state.path("achieved").asInt(-1) : -1;
            final var status = switch (achieved) {
                case 0 -> AchievementState.LOCKED;
                case 1 -> AchievementState.UNLOCKED;
                default -> AchievementState.UNKNOWN;
            };
            final var time = status == AchievementState.UNLOCKED
                    ? Math.max(0, state.path("unlocktime").asLong()) : 0L;
            result.add(new Achievement(id, name, description, status, time,
                    definition.path("icon").asText(""), definition.path("icongray").asText("")));
        }
        final var unknown =
                result.stream().filter((final var item) -> item.state() == AchievementState.UNKNOWN).count();
        final var resultNotice = unknown > 0 && notice.isBlank()
                ? "Steam did not expose the state of " + unknown + " achievement(s). Check Game details visibility; "
                + "unavailable states are not treated as locked." : notice;
        return new AchievementList(result, resultNotice);
    }

    private static void requireNotInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new StoneException("The Steam request was interrupted.");
        }
    }

    private JsonNode get(final String path, final String apiKey) {
        final var request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(20))
                .header("x-webapi-key", apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", "SteamStone/0.1.0")
                .GET().build();
        try {
            // Complete the body within the request timeout, rather than returning an unbounded stream.
            final var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            final var status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new StoneException(
                        "Steam rejected the request. Check your API key and profile access, then retry.");
            }
            if (status == 429)
                throw new StoneException("Steam is rate limiting requests. Wait a little before trying again.");
            if (status != 200)
                throw new StoneException("Steam is unavailable (HTTP " + status + "). Please try again later.");
            final var bytes = response.body();
            if (bytes.length > MAX_RESPONSE_BYTES)
                throw new StoneException("Steam's response was too large to load safely.");
            try {
                final var value = json.readTree(bytes);
                if (value == null)
                    throw new StoneException("Steam returned an empty response. Please retry.");
                return value;
            } catch (final IOException exception) {
                throw new StoneException("Steam returned an unexpected response. Please try again later.", exception);
            }
        } catch (final InterruptedException exception) {
            throw new StoneException("The Steam request was interrupted.", exception);
        } catch (final HttpTimeoutException ex) {
            throw new StoneException("Steam took too long to respond. Check your connection and retry.", ex);
        } catch (final IOException ex) {
            throw new StoneException("Couldn't reach Steam. Check your internet connection and try again.", ex);
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
