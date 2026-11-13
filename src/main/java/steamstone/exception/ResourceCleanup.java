package steamstone.exception;

/**
 * Runs every release action in the supplied order without masking the primary failure.
 */
public final class ResourceCleanup {
    public static void run(final Runnable... actions) {
        final var failure = release(null, actions);
        if (failure != null) {
            throw failure;
        }
    }

    public static StoneException afterFailure(final String message, final Throwable cause, final Runnable... actions) {
        return release(StoneException.wrap(message, cause), actions);
    }

    private static StoneException release(final StoneException primary, final Runnable... actions) {
        var failure = primary;
        for (final var action : actions) {
            try {
                action.run();
            } catch (final Throwable e) {
                if (failure == null) {
                    failure = StoneException.wrap("Could not release application resources.", e);
                } else if (e != failure) {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }
}
