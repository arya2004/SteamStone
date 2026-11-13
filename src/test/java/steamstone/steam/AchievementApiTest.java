package steamstone.steam;

import com.google.inject.Guice;
import com.google.inject.util.Modules;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import steamstone.exception.StoneException;
import steamstone.inject.SteamApiFixtureModule;
import steamstone.inject.SteamStoneModule;
import steamstone.model.achievement.AchievementState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AchievementApiTest {
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
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", (final var exchange) -> {
                try {
                    calls.incrementAndGet();
                    credentialsCorrect &= KEY.equals(exchange.getRequestHeaders().getFirst("x-webapi-key"))
                            && !exchange.getRequestURI().toString().contains(KEY)
                            && "GET".equals(exchange.getRequestMethod());
                    final var isSchema = exchange.getRequestURI().getPath().contains("GetSchemaForGame/v2");
                    final var body = (isSchema ? schema : player).getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(isSchema ? 200 : playerStatus, body.length);
                    try (final var output = exchange.getResponseBody()) {
                        output.write(body);
                    }

                } catch (final IOException e) {
                    throw new StoneException("Could not serve the fixture HTTP response.", e);
                }
            });
            server.start();
            final var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            api = Guice.createInjector(Modules.override(new SteamStoneModule())
                    .with(new SteamApiFixtureModule(base))).getInstance(SteamWebApi.class);

        } catch (final IOException e) {
            throw new StoneException("Could not start the fixture HTTP server.", e);
        }
    }

    @AfterEach
    public void stop() {
        server.stop(0);
    }

    @Test
    public void joinsByApiIdentifierRatherThanResponseOrder() {
        final var result = api.loadAchievements(KEY, ID, 10);
        final var first = result.achievements().getFirst();
        assertEquals("First step", first.name());
        assertEquals(AchievementState.UNLOCKED, first.state());
        assertEquals(12345, first.unlockTime());
        assertEquals(AchievementState.LOCKED, result.achievements().get(1).state());
        assertEquals("", result.achievements().get(1).description());
        assertTrue(result.notice().isBlank());
        assertTrue(credentialsCorrect);
        assertEquals(2, calls.get());
        assertThrows(UnsupportedOperationException.class, () -> result.achievements().clear());
    }

    @Test
    public void unavailablePlayerDataIsNeverAssumedLocked() {
        playerStatus = 403;
        final var result = api.loadAchievements(KEY, ID, 10);
        assertEquals(2, result.achievements().size());
        assertTrue(result.achievements().stream().allMatch((final var a) -> a.state() == AchievementState.UNKNOWN));
        assertFalse(result.notice().isBlank());
    }

    @Test
    public void missingOrMalformedStatesRemainUnknown() {
        player = """
                {"playerstats":{"success":true,"achievements":[{"apiname":"FIRST","achieved":2}]}}
                """;
        final var result = api.loadAchievements(KEY, ID, 10);
        assertTrue(result.achievements().stream().allMatch((final var a) -> a.state() == AchievementState.UNKNOWN));
        assertFalse(result.notice().isBlank());
    }

    @Test
    public void rejectsDifferentAccount() {
        player = player.replace(ID, "76561198000000001");
        assertThrows(StoneException.class, () -> api.loadAchievements(KEY, ID, 10));
    }

    @Test
    public void emptyDefinitionsSkipPlayerRequest() {
        schema = "{\"game\":{\"availableGameStats\":{\"achievements\":[]}}}";
        assertTrue(api.loadAchievements(KEY, ID, 10).achievements().isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    public void rejectsInvalidDefinitionsAndDuplicateIds() {
        schema = schema.replace("SECRET", "FIRST");
        assertThrows(StoneException.class, () -> api.loadAchievements(KEY, ID, 10));
        schema = "{\"game\":{\"availableGameStats\":{\"achievements\":{}}}}";
        assertThrows(StoneException.class, () -> api.loadAchievements(KEY, ID, 10));
    }

    @Test
    public void rejectsDuplicateStates() {
        player = player.replace("SECRET", "FIRST");
        assertThrows(StoneException.class, () -> api.loadAchievements(KEY, ID, 10));
    }

    @Test
    public void usesPlayerDescriptionWhenSchemaOmitsHiddenDescription() {
        player = player.replace("\"apiname\":\"SECRET\",\"achieved\":0",
                "\"apiname\":\"SECRET\",\"achieved\":0,\"description\":\"Find the secret door\"");
        assertEquals("Find the secret door", api.loadAchievements(KEY, ID, 10).achievements().get(1).description());
    }

    @Test
    public void validatesContextBeforeRequests() {
        assertThrows(StoneException.class, () -> api.loadAchievements(KEY, ID, -1));
        assertThrows(StoneException.class, () -> api.loadAchievements("bad", ID, 10));
        assertEquals(0, calls.get());
    }
}
