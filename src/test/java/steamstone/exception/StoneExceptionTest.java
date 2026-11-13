package steamstone.exception;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class StoneExceptionTest {
    @Test
    public void preservesContextAndOriginalCause() {
        final var cause = new IOException("Fixture failure");
        final var failure = new StoneException("Could not complete the operation.", cause);
        assertEquals("Could not complete the operation.", failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    public void wrapPreservesExistingApplicationFailuresWithoutAddingAnotherLayer() {
        final var cause = new IOException("Fixture failure");
        final var existing = new StoneException("Specific operation failed.", cause);
        assertSame(existing, StoneException.wrap("Generic failure.", existing));
        assertSame(cause, existing.getCause());
    }

    @Test
    public void wrapAddsContextToForeignFailuresAndPreservesInterruption() {
        try {
            final var cause = new InterruptedException("Fixture interruption");
            final var failure = StoneException.wrap("Interrupted operation.", cause);
            assertSame(cause, failure.getCause());
            assertEquals("Interrupted operation.", failure.getMessage());
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
            final var failure = new StoneException("The operation was interrupted.", cause);
            assertSame(cause, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void ordinaryFailuresDoNotInterruptTheThread() {
        final var failure = new StoneException("A validation failure.");
        assertEquals("A validation failure.", failure.getMessage());
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
