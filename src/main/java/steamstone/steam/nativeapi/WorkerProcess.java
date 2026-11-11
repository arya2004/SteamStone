package steamstone.steam.nativeapi;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import steamstone.exception.StoneException;

public final class WorkerProcess implements AutoCloseable {
    private final Process process;

    public WorkerProcess(final ProcessBuilder builder) {
        try {
            process = builder.start();
        } catch (final IOException exception) {
            throw new StoneException("Could not start the Steam worker.", exception);
        }
    }

    public boolean await(final long seconds) {
        try {
            return process.waitFor(seconds, TimeUnit.SECONDS);
        } catch (final InterruptedException exception) {
            throw new StoneException("Waiting for the Steam worker was interrupted. Changes may still sync.",
                    exception);
        }
    }

    public int exitValue() {
        return process.exitValue();
    }

    @Override
    public void close() {
        if (process.isAlive()) {
            process.destroyForcibly();
            if (!await(5)) {
                throw new StoneException("The Steam worker did not stop. Changes may still sync.");
            }
        }
    }
}
