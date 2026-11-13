package steamstone.steam.nativeapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

/**
 * Keeps credential-free protocol files scoped to a single worker call.
 */
public final class WorkerFiles implements AutoCloseable {
    private final Path directory;

    public WorkerFiles(final String prefix) {
        try {
            directory = Files.createTempDirectory(prefix).toAbsolutePath().normalize();
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    public Path directory() {
        return directory;
    }

    @Override
    public void close() {
        try (final var paths = Files.walk(directory)) {
            for (final var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }
}
