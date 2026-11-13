package steamstone.exception;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * A categorized application failure; display text never determines its meaning.
 */
@Getter
@Accessors(fluent = true)
public final class StoneException extends RuntimeException {
    private final ErrorCode code;

    public static StoneException wrap(final ErrorCode code, final Throwable e) {
        final var cause = unwrap(e);
        if (cause instanceof StoneException stone) {
            return stone;
        }
        return new StoneException(isCancellation(cause) ? ErrorCode.CANCELLED : code, cause);
    }

    public static ErrorCode codeOf(final Throwable e) {
        final var cause = unwrap(e);
        return cause instanceof StoneException stone ? stone.code()
                : isCancellation(cause) ? ErrorCode.CANCELLED : ErrorCode.APPLICATION;
    }

    public StoneException(final ErrorCode code) {
        this(code, null);
    }

    public StoneException(final ErrorCode code, final Throwable cause) {
        super(requireFailureCode(code).message(), cause);
        this.code = code;
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isCancellation(final Throwable e) {
        return e instanceof InterruptedException || e instanceof CancellationException;
    }

    private static ErrorCode requireFailureCode(final ErrorCode code) {
        if (code == null || code == ErrorCode.NONE) {
            throw new StoneException(ErrorCode.APPLICATION);
        }
        return code;
    }

    private static Throwable unwrap(final Throwable e) {
        var cause = e;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
