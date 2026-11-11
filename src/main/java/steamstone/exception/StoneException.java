package steamstone.exception;

/** A contextual application failure that preserves its original cause. */
public final class StoneException extends RuntimeException {
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
