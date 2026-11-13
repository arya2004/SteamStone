package steamstone.steam.nativeapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import steamstone.exception.StoneException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class WorkerResourcesTest {
    @Test
    public void removesNestedProtocolFilesOnClose() {
        final Path directory;
        try (final var files = new WorkerFiles("steamstone-cleanup-test-")) {
            directory = files.directory();
            assertTrue(directory.isAbsolute());
            final var nested = Files.createDirectories(directory.resolve("nested"));
            Files.writeString(nested.resolve("response.json"), "{}");
            assertTrue(Files.isRegularFile(nested.resolve("response.json")));
        } catch (final IOException exception) {
            throw new StoneException("Could not prepare the cleanup fixture.", exception);
        }
        assertFalse(Files.exists(directory));
    }

    @Test
    public void wrapsWorkerStartupFailuresWithTheirCause() {
        try (final var files = new WorkerFiles("steamstone-startup-test-")) {
            final var builder = new ProcessBuilder(files.directory().resolve("missing-executable").toString());
            final var failure = assertThrows(StoneException.class, () -> new WorkerProcess(builder));
            assertInstanceOf(IOException.class, failure.getCause());
        }
    }

    @Test
    public void interruptedWaitPreservesCancellationAndStillClosesTheWorker() {
        final var java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        final var builder = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                WorkerProcessProbe.class.getName());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try (final var process = new WorkerProcess(builder)) {
            Thread.currentThread().interrupt();
            try {
                final var failure = assertThrows(StoneException.class, () -> process.await(30));
                assertInstanceOf(InterruptedException.class, failure.getCause());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }
}
