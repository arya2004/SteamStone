package steamstone.steam;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import steamstone.model.Achievement;

import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AchievementApiTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String ID = "76561198000000000";
    private HttpServer server;
    private SteamWebApi api;
    private String schema = """
            {"game":{"availableGameStats":{"achievements":[
              {"name":"FIRST","displayName":"First step","description":"Start playing"},
              {"name":"SECRET","displayName":"Secret","hidden":1}]}}}
            """;
    private String player = """
            {"playerstats":{"steamID":"76561198000000000","success":true,"achievements":[
              {"apiname":"SECRET","achieved":0},
              {"apiname":"FIRST","achieved":1,"unlocktime":12345}]}}
            """;
    private int playerStatus = 200;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean credentialsCorrect = true;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            credentialsCorrect &= KEY.equals(exchange.getRequestHeaders().getFirst("x-webapi-key"))
                    && !exchange.getRequestURI().toString().contains(KEY)
                    && "GET".equals(exchange.getRequestMethod());
            boolean isSchema = exchange.getRequestURI().getPath().contains("GetSchemaForGame/v2");
            byte[] body = (isSchema ? schema : player).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(isSchema ? 200 : playerStatus, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        api = new SteamWebApi(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void joinsByApiIdentifierRatherThanResponseOrder() throws Exception {
        var result = api.loadAchievements(KEY, ID, 10);
        var first = result.achievements().getFirst();
        assertEquals("First step", first.name());
        assertEquals(Achievement.State.UNLOCKED, first.state());
        assertEquals(12345, first.unlockTime());
        assertEquals(Achievement.State.LOCKED, result.achievements().get(1).state());
        assertEquals("", result.achievements().get(1).description());
        assertTrue(result.notice().isBlank());
        assertTrue(credentialsCorrect);
        assertEquals(2, calls.get());
        assertThrows(UnsupportedOperationException.class, () -> result.achievements().clear());
    }

    @Test
    void unavailablePlayerDataIsNeverAssumedLocked() throws Exception {
        playerStatus = 403;
        var result = api.loadAchievements(KEY, ID, 10);
        assertEquals(2, result.achievements().size());
        assertTrue(result.achievements().stream().allMatch(a -> a.state() == Achievement.State.UNKNOWN));
        assertFalse(result.notice().isBlank());
    }

    @Test
    void missingOrMalformedStatesRemainUnknown() throws Exception {
        player = """
                {"playerstats":{"success":true,"achievements":[{"apiname":"FIRST","achieved":2}]}}
                """;
        var result = api.loadAchievements(KEY, ID, 10);
        assertTrue(result.achievements().stream().allMatch(a -> a.state() == Achievement.State.UNKNOWN));
        assertFalse(result.notice().isBlank());
    }

    @Test
    void rejectsDifferentAccount() {
        player = player.replace(ID, "76561198000000001");
        assertThrows(SteamApiException.class, () -> api.loadAchievements(KEY, ID, 10));
    }

    @Test
    void emptyDefinitionsSkipPlayerRequest() throws Exception {
        schema = "{\"game\":{\"availableGameStats\":{\"achievements\":[]}}}";
        assertTrue(api.loadAchievements(KEY, ID, 10).achievements().isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsInvalidDefinitionsAndDuplicateIds() {
        schema = schema.replace("SECRET", "FIRST");
        assertThrows(SteamApiException.class, () -> api.loadAchievements(KEY, ID, 10));
        schema = "{\"game\":{\"availableGameStats\":{\"achievements\":{}}}}";
        assertThrows(SteamApiException.class, () -> api.loadAchievements(KEY, ID, 10));
    }

    @Test
    void rejectsDuplicateStates() {
        player = player.replace("SECRET", "FIRST");
        assertThrows(SteamApiException.class, () -> api.loadAchievements(KEY, ID, 10));
    }

    @Test
    void usesPlayerDescriptionWhenSchemaOmitsHiddenDescription() throws Exception {
        player = player.replace("\"apiname\":\"SECRET\",\"achieved\":0",
                "\"apiname\":\"SECRET\",\"achieved\":0,\"description\":\"Find the secret door\"");
        assertEquals("Find the secret door", api.loadAchievements(KEY, ID, 10).achievements().get(1).description());
    }

    @Test
    void validatesContextBeforeRequests() {
        assertThrows(SteamApiException.class, () -> api.loadAchievements(KEY, ID, -1));
        assertThrows(SteamApiException.class, () -> api.loadAchievements("bad", ID, 10));
        assertEquals(0, calls.get());
    }
}
