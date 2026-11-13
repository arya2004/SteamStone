package steamstone.exception;

/**
 * A contextual application failure that preserves its original cause.
 */
public final class StoneException extends RuntimeException {
    public static StoneException wrap(final String message, final Throwable cause) {
        return cause instanceof StoneException stone ? stone : new StoneException(message, cause);
    }

    public StoneException(final String message) {
        super(message);
    }

    public StoneException(final String message, final Throwable cause) {
        super(message, cause);
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
