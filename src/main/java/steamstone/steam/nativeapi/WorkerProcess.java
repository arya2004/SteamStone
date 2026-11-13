package steamstone.steam.nativeapi;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

public final class WorkerProcess implements AutoCloseable {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final Process process;

    public WorkerProcess(final ProcessBuilder builder) {
        try {
            process = builder.start();
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.STEAM_STORAGE, e);
        }
    }

    public boolean await(final long seconds) {
        try {
            return process.waitFor(seconds, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            throw new StoneException(ErrorCode.CANCELLED,
                    e);
        }
    }

    public int exitValue() {
        return process.exitValue();
    }

    @Override
    public void close() {
        if (process.isAlive()) {
            process.destroyForcibly();
            if (!await(SHUTDOWN_TIMEOUT_SECONDS)) {
                throw new StoneException(ErrorCode.STEAM_STORAGE);
            }
        }
    }
}
