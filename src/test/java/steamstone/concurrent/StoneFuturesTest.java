package steamstone.concurrent;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class StoneFuturesTest {
    @Test
    public void preservesApplicationFailuresAcrossTheFutureBoundary() {
        final var original = new StoneException(ErrorCode.APPLICATION);
        final var e = assertThrows(StoneException.class,
                () -> StoneFutures.await(CompletableFuture.failedFuture(original)));
        assertSame(original, e);
    }

    @Test
    public void wrapsForeignFailuresOnceWithTheirOriginalCause() {
        final var original = new IOException("Fixture failure.");
        final var e = assertThrows(StoneException.class,
                () -> StoneFutures.await(CompletableFuture.failedFuture(original)));
        assertSame(original, e.getCause());
    }

    @Test
    public void returnsSuccessfulResults() {
        assertEquals("result", StoneFutures.await(CompletableFuture.completedFuture("result")));
    }
}
