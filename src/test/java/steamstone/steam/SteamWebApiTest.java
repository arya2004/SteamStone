package steamstone.steam;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SteamWebApiTest {
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

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            credentialInQuery |= exchange.getRequestURI().toString().contains(KEY);
            suppliedHeader = exchange.getRequestHeaders().getFirst("x-webapi-key");
            String path = exchange.getRequestURI().getPath();
            String body;
            int status;
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
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        api = new SteamWebApi(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void loadsTheAccountLibraryWithoutACandidateCatalog() throws Exception {
        var result = api.loadLibrary(KEY, ID);
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

    @Test void resolvesVanityProfilesThroughTheDocumentedApi() throws Exception {
        var result = api.loadLibrary(KEY, "https://steamcommunity.com/id/fixture-user");
        assertEquals(ID, result.steamId());
        assertEquals(3, requests.get());
    }

    @Test void rejectsInvalidCredentialsBeforeMakingRequests() {
        assertThrows(SteamApiException.class, () -> api.loadLibrary("bad-key", ID));
        assertEquals(0, requests.get());
    }

    @Test void doesNotTreatAPrivateOrUnavailableLibraryAsEmpty() {
        ownedBody = "{\"response\":{}}";
        var error = assertThrows(SteamApiException.class, () -> api.loadLibrary(KEY, ID));
        assertTrue(error.getMessage().contains("did not expose"));
    }

    @Test void acceptsAnExplicitZeroCountWithoutInventingGames() throws Exception {
        ownedBody = "{\"response\":{\"game_count\":0}}";
        assertTrue(api.loadLibrary(KEY, ID).games().isEmpty());
    }

    @Test void rejectsIncompleteSnapshots() {
        ownedBody = "{\"response\":{\"game_count\":3,\"games\":[{\"appid\":10,\"name\":\"One\"}]}}";
        var error = assertThrows(SteamApiException.class, () -> api.loadLibrary(KEY, ID));
        assertTrue(error.getMessage().contains("partial"));
    }

    @Test void rejectsMalformedAndInvalidGameData() {
        ownedBody = "not-json";
        assertThrows(SteamApiException.class, () -> api.loadLibrary(KEY, ID));
        ownedBody = "{\"response\":{\"game_count\":1,\"games\":[{\"appid\":-1}]}}";
        assertThrows(SteamApiException.class, () -> api.loadLibrary(KEY, ID));
    }

    @Test void returnsUsefulCredentialFreeHttpErrors() {
        for (int status : new int[]{403, 429, 503, 302}) {
            ownedStatus = status;
            ownedBody = "REMOTE BODY THAT MUST NEVER BE SHOWN " + KEY;
            var error = assertThrows(SteamApiException.class, () -> api.loadLibrary(KEY, ID));
            assertFalse(error.getMessage().contains(KEY));
            assertFalse(error.getMessage().contains("REMOTE BODY"));
        }
    }

    @Test void optionalProfileFailureDoesNotDiscardTheLibrary() throws Exception {
        summaryStatus = 503;
        var result = api.loadLibrary(KEY, ID);
        assertEquals(2, result.games().size());
        assertEquals(ID, result.playerName());
    }
}
