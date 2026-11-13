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

    public static StoneException afterFailure(final ErrorCode code, final Throwable cause, final Runnable... actions) {
        return release(StoneException.wrap(code, cause), actions);
    }

    private static StoneException release(final StoneException primary, final Runnable... actions) {
        var failure = primary;
        for (final var action : actions) {
            try {
                action.run();
            } catch (final Throwable e) {
                if (failure == null) {
                    failure = StoneException.wrap(ErrorCode.APPLICATION, e);
                } else if (e != failure) {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }
}
