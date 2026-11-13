package steamstone.steam.nativeapi;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

/**
 * Credential-free child process used to verify worker cancellation and cleanup.
 */
public final class WorkerProcessProbe {

    public static void main(final String[] args) {
        try {
            Thread.sleep(30000);
        } catch (final InterruptedException e) {
            throw new StoneException(ErrorCode.CANCELLED, e);
        }
    }
}
