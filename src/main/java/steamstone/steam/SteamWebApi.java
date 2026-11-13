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
    private static final String API_BASE_URI = "https://api.steampowered.com/";
    private static final Duration API_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final String WEB_API_KEY_PATTERN = "[a-fA-F0-9]{32}";
    private static final long MAX_APP_ID = 0xffffffffL;

    private static final String RESPONSE_KEY = "response";
    private static final String STEAM_ID_KEY = "steamid";
    private static final String PLAYERS_KEY = "players";
    private static final String PERSONA_NAME_KEY = "personaname";
    private static final String GAME_COUNT_KEY = "game_count";
    private static final String GAMES_KEY = "games";
    private static final String APP_ID_KEY = "appid";
    private static final String NAME_KEY = "name";
    private static final String PLAYTIME_KEY = "playtime_forever";
    private static final String LAST_PLAYED_KEY = "rtime_last_played";
    private static final String ICON_HASH_KEY = "img_icon_url";
    private static final String GAME_KEY = "game";
    private static final String GAME_STATS_KEY = "availableGameStats";
    private static final String ACHIEVEMENTS_KEY = "achievements";
    private static final String PLAYER_STATS_KEY = "playerstats";
    private static final String SUCCESS_KEY = "success";
    private static final String PLAYER_STEAM_ID_KEY = "steamID";
    private static final String API_NAME_KEY = "apiname";
    private static final String DISPLAY_NAME_KEY = "displayName";
    private static final String DESCRIPTION_KEY = "description";
    private static final String ACHIEVED_KEY = "achieved";
    private static final String UNLOCK_TIME_KEY = "unlocktime";
    private static final String ICON_KEY = "icon";
    private static final String LOCKED_ICON_KEY = "icongray";
    private static final String OWNED_GAMES_PATH = "IPlayerService/GetOwnedGames/v1/?input_json=";
    private static final String PLAYER_SUMMARY_PATH = "ISteamUser/GetPlayerSummaries/v2/?steamids=";
    private static final String VANITY_RESOLUTION_PATH = "ISteamUser/ResolveVanityURL/v1/?vanityurl=";
    private static final String GAME_SCHEMA_PATH = "ISteamUserStats/GetSchemaForGame/v2/?appid=";
    private static final String PLAYER_ACHIEVEMENTS_PATH = "ISteamUserStats/GetPlayerAchievements/v1/?appid=";
    private static final String LANGUAGE_QUERY = "&l=english";
    private static final String STEAM_ID_QUERY = "&steamid=";
    private static final String API_KEY_HEADER = "x-webapi-key";
    private static final String ACCEPT_HEADER = "Accept";
    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String USER_AGENT = "SteamStone/0.1.0";
    private static final String EMPTY_VALUE = "";
    private static final String OWNED_GAMES_INPUT =
            "{\"steamid\":\"%s\",\"include_appinfo\":true,\"include_played_free_games\":true}";
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int UNKNOWN_ACHIEVEMENT_VALUE = -1;
    private static final int LOCKED_ACHIEVEMENT_VALUE = 0;
    private static final int UNLOCKED_ACHIEVEMENT_VALUE = 1;

    private final HttpClient client;
    private final URI base;
    private final ObjectMapper json = new ObjectMapper();

    public SteamWebApi() {
        this(HttpClient.newBuilder().connectTimeout(API_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), URI.create(API_BASE_URI));
    }

    public SteamLibrary loadLibrary(final String apiKey, final String profileInput) {
        final var key = apiKey == null ? EMPTY_VALUE : apiKey.strip();
        if (!key.matches(WEB_API_KEY_PATTERN)) {
            throw new StoneException("Enter the 32-character Steam Web API key from steamcommunity.com/dev/apikey.");
        }
        final var profile = SteamProfile.parse(profileInput);
        final var steamId = profile.needsResolution() ? resolveProfile(profile, key) : profile.value();

        final var input = OWNED_GAMES_INPUT.formatted(steamId);
        final var response = get(OWNED_GAMES_PATH + encode(input), key).path(RESPONSE_KEY);
        final var library = parseLibrary(response, steamId, steamId);

        // Optional display data must not hide a valid library snapshot.
        final var playerName = StoneFutures.await(CompletableFuture.completedFuture(steamId)
                .thenApply((final var id) -> get(PLAYER_SUMMARY_PATH + id, key)
                        .path(RESPONSE_KEY).path(PLAYERS_KEY))
                .handle((final var players, final var e) -> {
                    requireNotInterrupted();
                    if (e != null || !players.isArray() || players.isEmpty()) {
                        return steamId;
                    }
                    final var candidate = players.get(0).path(PERSONA_NAME_KEY).asText();
                    return candidate.isBlank() ? steamId : candidate;
                }));
        return new SteamLibrary(steamId, playerName, library.games(), library.fetchedAt());
    }

    private String resolveProfile(final SteamProfile profile, final String key) {
        final var result = get(VANITY_RESOLUTION_PATH + encode(profile.value()), key);
        final var steamId = result.path(RESPONSE_KEY).path(STEAM_ID_KEY).asText();
        if (!steamId.matches(SteamConstants.STEAM_ID_PATTERN)) {
            throw new StoneException("That Steam profile could not be found. Check the URL or use your SteamID64.");
        }
        return steamId;
    }

    public SteamLibrary parseLibrary(final JsonNode response, final String steamId, final String playerName) {
        if (!response.isObject() || !response.path(GAME_COUNT_KEY).canConvertToInt()
                || !response.path(GAME_COUNT_KEY).isIntegralNumber()) {
            throw new StoneException(
                    "Steam did not expose this library. Check the profile and set Game details to Public in Steam " +
                            "privacy settings, then retry.");
        }
        final var count = response.path(GAME_COUNT_KEY).asInt();
        final var entries = response.path(GAMES_KEY);
        if (count < 0 || (count > 0 && !entries.isArray())) {
            throw new StoneException("Steam returned an incomplete library. Please try again.");
        }
        final var games = new ArrayList<Game>();
        final var seen = new HashSet<Long>();
        if (entries.isArray()) {
            for (final var entry : entries) {
                final var id = entry.path(APP_ID_KEY);
                if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0
                        || id.asLong() > MAX_APP_ID) {
                    throw new StoneException("Steam returned an invalid game entry. Please retry.");
                }
                final var appId = id.asLong();
                if (!seen.add(appId))
                    continue;
                final var suppliedName = entry.path(NAME_KEY).asText(EMPTY_VALUE).strip();
                final var name = suppliedName.isEmpty() ? "App " + appId : suppliedName;
                games.add(new Game(appId, name,
                        Math.max(0, entry.path(PLAYTIME_KEY).asLong()),
                        Math.max(0, entry.path(LAST_PLAYED_KEY).asLong()),
                        entry.path(ICON_HASH_KEY).asText(EMPTY_VALUE)));
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
        if (apiKey == null || !apiKey.matches(WEB_API_KEY_PATTERN) || steamId == null
                || !steamId.matches(SteamConstants.STEAM_ID_PATTERN) || appId <= 0 ||
                appId > MAX_APP_ID) {
            throw new StoneException("A valid account, API key, and game are required.");
        }
        final var schema =
                get(GAME_SCHEMA_PATH + appId + LANGUAGE_QUERY, apiKey).path(GAME_KEY);
        if (!schema.isObject() || schema.isEmpty()) {
            throw new StoneException("Steam did not provide achievement definitions for this game.");
        }
        final var definitions = schema.path(GAME_STATS_KEY).path(ACHIEVEMENTS_KEY);
        if (definitions.isMissingNode() || definitions.isNull() || (definitions.isArray() && definitions.isEmpty())) {
            return new AchievementList(List.of(), "Steam returned no achievement definitions for this game.");
        }
        if (!definitions.isArray())
            throw new StoneException("Steam returned unexpected achievement definitions.");

        final var snapshot = StoneFutures.await(CompletableFuture.completedFuture(steamId)
                .thenApply((final var id) -> get(PLAYER_ACHIEVEMENTS_PATH + appId
                        + STEAM_ID_QUERY + id + LANGUAGE_QUERY, apiKey).path(PLAYER_STATS_KEY))
                .handle((final var player, final var e) -> {
                    requireNotInterrupted();
                    return e == null ? new PlayerSnapshot(player, EMPTY_VALUE)
                            : new PlayerSnapshot(null, "Achievement states are unavailable. Check your profile "
                            + "visibility and reload before editing.");
                }));
        return combineAchievements(definitions, snapshot.player(), steamId, snapshot.notice());
    }

    public AchievementList combineAchievements(final JsonNode definitions, final JsonNode player, final String steamId,
                                               final String notice) {
        final var states = new HashMap<String, JsonNode>();
        if (player != null && player.path(SUCCESS_KEY).asBoolean(false)) {
            final var returnedId = player.path(PLAYER_STEAM_ID_KEY).asText(steamId);
            if (!returnedId.equals(steamId))
                throw new StoneException("Steam returned achievement data for a different account.");
            final var entries = player.path(ACHIEVEMENTS_KEY);
            if (entries.isArray()) {
                for (final var entry : entries) {
                    final var name = entry.path(API_NAME_KEY).asText();
                    if (!name.isBlank() && states.put(name, entry) != null) {
                        throw new StoneException("Steam returned duplicate achievement states. Please retry.");
                    }
                }
            }
        }
        final var result = new ArrayList<Achievement>();
        final var names = new HashSet<String>();
        for (final var definition : definitions) {
            final var id = definition.path(NAME_KEY).asText();
            if (id.isBlank() || !names.add(id))
                throw new StoneException("Steam returned invalid achievement identifiers.");
            final var suppliedName = definition.path(DISPLAY_NAME_KEY).asText(id);
            final var name = suppliedName.isBlank() ? id : suppliedName;
            final var state = states.get(id);
            final var suppliedDescription = definition.path(DESCRIPTION_KEY).asText(EMPTY_VALUE);
            final var description = suppliedDescription.isBlank() && state != null
                    ? state.path(DESCRIPTION_KEY).asText(EMPTY_VALUE) : suppliedDescription;
            final var achieved = state != null && state.path(ACHIEVED_KEY).isIntegralNumber()
                    ? state.path(ACHIEVED_KEY).asInt(UNKNOWN_ACHIEVEMENT_VALUE) : UNKNOWN_ACHIEVEMENT_VALUE;
            final var status = switch (achieved) {
                case LOCKED_ACHIEVEMENT_VALUE -> AchievementState.LOCKED;
                case UNLOCKED_ACHIEVEMENT_VALUE -> AchievementState.UNLOCKED;
                default -> AchievementState.UNKNOWN;
            };
            final var time = status == AchievementState.UNLOCKED
                    ? Math.max(0, state.path(UNLOCK_TIME_KEY).asLong()) : 0L;
            result.add(new Achievement(id, name, description, status, time,
                    definition.path(ICON_KEY).asText(EMPTY_VALUE),
                    definition.path(LOCKED_ICON_KEY).asText(EMPTY_VALUE)));
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
                .timeout(REQUEST_TIMEOUT)
                .header(API_KEY_HEADER, apiKey)
                .header(ACCEPT_HEADER, JSON_MEDIA_TYPE)
                .header(USER_AGENT_HEADER, USER_AGENT)
                .GET().build();
        try {
            // Complete the body within the request timeout, rather than returning an unbounded stream.
            final var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            final var status = response.statusCode();
            if (status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN) {
                throw new StoneException(
                        "Steam rejected the request. Check your API key and profile access, then retry.");
            }
            if (status == HTTP_TOO_MANY_REQUESTS)
                throw new StoneException("Steam is rate limiting requests. Wait a little before trying again.");
            if (status != HTTP_OK)
                throw new StoneException("Steam is unavailable (HTTP " + status + "). Please try again later.");
            final var bytes = response.body();
            if (bytes.length > MAX_RESPONSE_BYTES)
                throw new StoneException("Steam's response was too large to load safely.");
            try {
                final var value = json.readTree(bytes);
                if (value == null)
                    throw new StoneException("Steam returned an empty response. Please retry.");
                return value;
            } catch (final IOException e) {
                throw new StoneException("Steam returned an unexpected response. Please try again later.", e);
            }
        } catch (final InterruptedException e) {
            throw new StoneException("The Steam request was interrupted.", e);
        } catch (final HttpTimeoutException e) {
            throw new StoneException("Steam took too long to respond. Check your connection and retry.", e);
        } catch (final IOException e) {
            throw new StoneException("Couldn't reach Steam. Check your internet connection and try again.", e);
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
