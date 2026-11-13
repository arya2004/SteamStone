package steamstone.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

/**
 * Exposes application failures rather than leaking completion wrappers to callers.
 */
public final class StoneFutures {

    public static <T> T await(final CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (final CompletionException e) {
            throw StoneException.wrap(ErrorCode.APPLICATION, e);
        }
    }
}
