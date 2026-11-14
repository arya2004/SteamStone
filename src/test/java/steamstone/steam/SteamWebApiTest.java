package steamstone.steam;

import com.google.inject.util.Modules;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;
import steamstone.inject.SteamApiFixtureModule;
import steamstone.inject.SteamStoneModule;
import steamstone.inject.SteamStoneInjector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SteamWebApiTest {
    // Deliberately synthetic credential, never read from the developer's key file.
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String ID = "76561198000000000";
    private HttpServer server;
    private SteamWebApi api;
    private volatile int ownedStatus = 200;
    private volatile String ownedBody = """
            {"response":{"game_count":2,"games":[
              {"appid":20,"name":"Zeta","playtime_forever":120,"rtime_last_played":100},
              {"appid":10,"name":"Alpha","playtime_forever":0,"img_icon_url":"abc"}
            ]}}
            """;
    private volatile int summaryStatus = 200;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile boolean credentialInQuery;
    private volatile String suppliedHeader;
    private volatile String libraryQuery;

    @BeforeEach
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", (final var exchange) -> {
                try {
                    requests.incrementAndGet();
                    credentialInQuery |= exchange.getRequestURI().toString().contains(KEY);
                    suppliedHeader = exchange.getRequestHeaders().getFirst("x-webapi-key");
                    final var path = exchange.getRequestURI().getPath();
                    final String body;
                    final int status;
                    if (path.contains("GetOwnedGames")) {
                        libraryQuery = exchange.getRequestURI().getQuery();
                        body = ownedBody;
                        status = ownedStatus;
                    } else if (path.contains("ResolveVanityURL")) {
                        body = "{\"response\":{\"success\":1,\"steamid\":\"" + ID + "\"}}";
                        status = 200;
                    } else {
                        body = "{\"response\":{\"players\":[{\"personaname\":\"Fixture User\"}]}}";
                        status = summaryStatus;
                    }
                    final var bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (final var stream = exchange.getResponseBody()) {
                        stream.write(bytes);
                    }

                } catch (final IOException e) {
                    throw new StoneException(ErrorCode.APPLICATION, e);
                }
            });
            server.start();
            final var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            api = SteamStoneInjector.create(Modules.override(new SteamStoneModule())
                    .with(new SteamApiFixtureModule(base))).getInstance(SteamWebApi.class);

        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    @AfterEach
    public void stop() {
        api.close();
        server.stop(0);
    }

    @Test
    public void loadsTheAccountLibraryWithoutACandidateCatalog() {
        final var result = api.loadLibrary(KEY, ID);
        assertEquals(2, result.games().size());
        assertEquals("Alpha", result.games().getFirst().name());
        assertEquals(120, result.playtimeMinutes());
        assertEquals("Fixture User", result.playerName());
        assertEquals(ID, result.steamId());
        assertEquals(KEY, suppliedHeader);
        assertFalse(credentialInQuery);
        assertTrue(libraryQuery.contains("include_played_free_games"));
        assertTrue(libraryQuery.contains("include_appinfo"));
        assertEquals(2, requests.get());
        assertThrows(UnsupportedOperationException.class, () -> result.games().clear());
    }

    @Test
    public void resolvesVanityProfilesThroughTheDocumentedApi() {
        final var result = api.loadLibrary(KEY, "https://steamcommunity.com/id/fixture-user");
        assertEquals(ID, result.steamId());
        assertEquals(3, requests.get());
    }

    @Test
    public void rejectsInvalidCredentialsBeforeMakingRequests() {
        assertThrows(StoneException.class, () -> api.loadLibrary("bad-key", ID));
        assertEquals(0, requests.get());
    }

    @Test
    public void doesNotTreatAPrivateOrUnavailableLibraryAsEmpty() {
        ownedBody = "{\"response\":{}}";
        final var error = assertThrows(StoneException.class, () -> api.loadLibrary(KEY, ID));
        assertEquals(ErrorCode.STEAM_API, error.code());
    }

    @Test
    public void acceptsAnExplicitZeroCountWithoutInventingGames() {
        ownedBody = "{\"response\":{\"game_count\":0}}";
        assertTrue(api.loadLibrary(KEY, ID).games().isEmpty());
    }

    @Test
    public void rejectsIncompleteSnapshots() {
        ownedBody = "{\"response\":{\"game_count\":3,\"games\":[{\"appid\":10,\"name\":\"One\"}]}}";
        final var error = assertThrows(StoneException.class, () -> api.loadLibrary(KEY, ID));
        assertEquals(ErrorCode.STEAM_API, error.code());
    }

    @Test
    public void rejectsMalformedAndInvalidGameData() {
        ownedBody = "not-json";
        final var e = assertThrows(StoneException.class, () -> api.loadLibrary(KEY, ID));
        assertEquals(ErrorCode.STEAM_API, e.code());
        assertInstanceOf(JsonProcessingException.class, e.getCause());
        ownedBody = "{\"response\":{\"game_count\":1,\"games\":[{\"appid\":-1}]}}";
        assertThrows(StoneException.class, () -> api.loadLibrary(KEY, ID));
    }

    @Test
    public void returnsUsefulCredentialFreeHttpErrors() {
        for (final var status : new int[]{403, 429, 503, 302}) {
            ownedStatus = status;
            ownedBody = "REMOTE BODY THAT MUST NEVER BE SHOWN " + KEY;
            final var error = assertThrows(StoneException.class, () -> api.loadLibrary(KEY, ID));
            assertEquals(ErrorCode.STEAM_API, error.code());
            assertEquals(ErrorCode.STEAM_API.message(), error.getMessage());
            assertFalse(error.getMessage().contains(KEY));
            assertFalse(error.getMessage().contains("REMOTE BODY"));
        }
    }

    @Test
    public void optionalProfileFailureDoesNotDiscardTheLibrary() {
        summaryStatus = 503;
        final var result = api.loadLibrary(KEY, ID);
        assertEquals(2, result.games().size());
        assertEquals(ID, result.playerName());
    }

    @Test
    public void wrapsInterruptedRequestsWithoutClearingCancellation() {
        Thread.currentThread().interrupt();
        try {
            final var failure = assertThrows(StoneException.class, () -> api.loadLibrary(KEY, ID));
            assertEquals(ErrorCode.CANCELLED, failure.code());
            assertTrue(failure.getCause() instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
