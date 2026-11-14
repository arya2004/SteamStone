package steamstone.ui.verification;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

/**
 * Synthetic empty libraries avoid starting any native Steam session during UI checks.
 */
public final class SavedLoginApiFixture implements AutoCloseable {
    private static final String SECOND_ID = "76561198000000001";
    private final HttpServer server;
    private final String library;
    public final AtomicInteger requests = new AtomicInteger();
    public volatile int status = 200;

    public SavedLoginApiFixture() {
        this(0);
    }

    public SavedLoginApiFixture(final int games) {
        library = "{\"response\":{\"game_count\":" + games + ",\"games\":[" + IntStream.rangeClosed(1, games)
                .mapToObj((final var id) -> "{\"appid\":" + id + ",\"name\":\"Game " + id + "\"}")
                .collect(Collectors.joining(",")) + "]}}";
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::respond);
            server.start();
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    public URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    private void respond(final HttpExchange exchange) {
        requests.incrementAndGet();
        final var second = exchange.getRequestURI().toString().contains(SECOND_ID);
        final var body = exchange.getRequestURI().getPath().contains("GetOwnedGames")
                ? library
                : exchange.getRequestURI().getPath().contains("GetSchemaForGame")
                ? "{\"game\":{\"availableGameStats\":{\"achievements\":[]}}}"
                : "{\"response\":{\"players\":[{\"personaname\":\"" + (second ? "Second" : "First") + "\"}]}}";
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            try (final var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
