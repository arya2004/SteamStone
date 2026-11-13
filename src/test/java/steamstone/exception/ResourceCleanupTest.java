package steamstone.exception;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ResourceCleanupTest {
    @Test
    public void releasesAllResourcesInOrderEvenWhenEarlierReleasesFail() {
        final var released = new ArrayList<String>();
        final var first = new IllegalStateException("First release failed.");
        final var second = new LinkageError("Second release failed.");
        final var e = assertThrows(StoneException.class, () -> ResourceCleanup.run(
                () -> {
                    released.add("stats");
                    throw first;
                },
                () -> {
                    released.add("utils");
                    throw second;
                },
                () -> released.add("user"),
                () -> released.add("shutdown")));
        assertEquals(List.of("stats", "utils", "user", "shutdown"), released);
        assertSame(first, e.getCause());
        assertEquals(1, e.getSuppressed().length);
        assertSame(second, e.getSuppressed()[0]);
    }

    @Test
    public void initializationFailureRemainsPrimaryWhenCleanupAlsoFails() {
        final var original = new StoneException(ErrorCode.APPLICATION);
        final var cleanup = new IllegalStateException("Release failed.");
        final var e = ResourceCleanup.afterFailure(ErrorCode.APPLICATION, original, () -> {
            throw cleanup;
        });
        assertSame(original, e);
        assertSame(cleanup, e.getSuppressed()[0]);
    }

    @Test
    public void repeatedFailureDoesNotAttemptSelfSuppression() {
        final var original = new StoneException(ErrorCode.APPLICATION);
        assertSame(original, ResourceCleanup.afterFailure(ErrorCode.APPLICATION, original, () -> {
            throw original;
        }));
        assertEquals(0, original.getSuppressed().length);
    }

    @Test
    public void successfulReleasesCompleteNormally() {
        final var released = new ArrayList<String>();
        ResourceCleanup.run(() -> released.add("first"), () -> released.add("second"));
        assertEquals(List.of("first", "second"), released);
    }
}
