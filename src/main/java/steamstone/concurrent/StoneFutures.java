package steamstone.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import steamstone.exception.StoneException;

/**
 * Exposes application failures rather than leaking completion wrappers to callers.
 */
public final class StoneFutures {
    private StoneFutures() {
    }

    public static <T> T await(final CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (final CompletionException exception) {
            final var cause = exception.getCause();
            throw new StoneException(cause instanceof StoneException stone ? stone.getMessage() : "The operation failed.",
                    cause == null ? exception : cause);
        }
    }
}
