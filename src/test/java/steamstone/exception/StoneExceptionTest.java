package steamstone.exception;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class StoneExceptionTest {
    @Test
    public void preservesContextAndOriginalCause() {
        final var cause = new IOException("Fixture failure");
        final var failure = new StoneException(ErrorCode.APPLICATION, cause);
        assertEquals(ErrorCode.APPLICATION, failure.code());
        assertEquals(ErrorCode.APPLICATION.message(), failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    public void wrapPreservesExistingApplicationFailuresWithoutAddingAnotherLayer() {
        final var cause = new IOException("Fixture failure");
        final var existing = new StoneException(ErrorCode.APPLICATION, cause);
        assertSame(existing, StoneException.wrap(ErrorCode.APPLICATION, existing));
        assertSame(cause, existing.getCause());
    }

    @Test
    public void wrapAddsContextToForeignFailuresAndPreservesInterruption() {
        try {
            final var cause = new InterruptedException("Fixture interruption");
            final var failure = StoneException.wrap(ErrorCode.CANCELLED, cause);
            assertSame(cause, failure.getCause());
            assertEquals(ErrorCode.CANCELLED, failure.code());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void restoresInterruptWhenWrappingInterruptedException() {
        assertFalse(Thread.currentThread().isInterrupted());
        try {
            final var cause = new InterruptedException("Fixture interruption");
            final var failure = new StoneException(ErrorCode.CANCELLED, cause);
            assertSame(cause, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void failureClassificationSurvivesFutureWrappersAndIgnoresCauseMessages() {
        final var original = new StoneException(ErrorCode.STEAM_API, new IOException("Different diagnostic text."));
        final var wrapped = new CompletionException(new ExecutionException(original));
        assertEquals(ErrorCode.STEAM_API, StoneException.codeOf(wrapped));
        assertSame(original, StoneException.wrap(ErrorCode.APPLICATION, wrapped));
        assertEquals(ErrorCode.APPLICATION, StoneException.codeOf(new IOException("Steam API error.")));
    }

    @Test
    public void classifiesCancellationWithoutExaminingItsMessage() {
        final var cause = new CancellationException("Unrelated text.");
        assertEquals(ErrorCode.CANCELLED, StoneException.codeOf(new CompletionException(cause)));
        assertEquals(ErrorCode.CANCELLED, StoneException.wrap(ErrorCode.APPLICATION, cause).code());
    }

    @Test
    public void exceptionsRequireAnActualFailureCode() {
        assertEquals(ErrorCode.APPLICATION,
                assertThrows(StoneException.class, () -> new StoneException(ErrorCode.NONE)).code());
        assertEquals(ErrorCode.APPLICATION,
                assertThrows(StoneException.class, () -> new StoneException(null)).code());
    }

    @Test
    public void ordinaryFailuresDoNotInterruptTheThread() {
        final var failure = new StoneException(ErrorCode.APPLICATION);
        assertEquals(ErrorCode.APPLICATION, failure.code());
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
