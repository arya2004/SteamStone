package steamstone.steam.nativeapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import steamstone.Main;
import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Status;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public final class WorkerLauncherTest {
    private static final String LAUNCHER_PROPERTY = "jpackage.app-path";

    @Test
    public void reusesTheExactInstalledExecutableWithSpaces(final @TempDir Path directory) {
        final var launcher = directory.resolve("SteamStone app with spaces.exe");
        try {
            Files.writeString(launcher, "fixture");
            assertTrue(launcher.toFile().setExecutable(true));
            final var previous = System.setProperty(LAUNCHER_PROPERTY, launcher.toString());
            try {
                final var command = WorkerLauncher.command();
                assertEquals(2, command.size());
                assertEquals(launcher.toString(), command.getFirst());
                assertEquals("--steam-worker", command.getLast());
                assertFalse(command.getFirst().contains("SteamStoneWorker"));
            } finally {
                restore(previous);
            }
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    @Test
    public void rejectsRelativeInstalledLaunchersInsteadOfResolvingAgainstTheWorkingDirectory() {
        final var previous = System.setProperty(LAUNCHER_PROPERTY, "SteamStone.exe");
        try {
            assertThrows(StoneException.class, WorkerLauncher::command);
        } finally {
            restore(previous);
        }
    }

    @Test
    public void missingInstalledLauncherDoesNotFallBackToJava(final @TempDir Path directory) {
        final var previous = System.setProperty(LAUNCHER_PROPERTY, directory.resolve("missing.exe").toString());
        try {
            assertThrows(StoneException.class, WorkerLauncher::command);
        } finally {
            restore(previous);
        }
    }

    @Test
    public void developmentUsesAbsoluteJavaAndClasspathEntries() {
        final var previous = System.clearProperty(LAUNCHER_PROPERTY);
        try {
            final var command = WorkerLauncher.command();
            assertTrue(Path.of(command.getFirst()).isAbsolute());
            final var classpath = command.get(command.indexOf("-cp") + 1);
            assertTrue(Arrays.stream(classpath.split(File.pathSeparator)).allMatch((final var entry) ->
                    Path.of(entry).isAbsolute() && Files.exists(Path.of(entry))));
            assertEquals(Main.class.getName(), command.get(command.size() - 2));
            assertEquals("--steam-worker", command.getLast());
        } finally {
            restore(previous);
        }
    }

    @Test
    public void failedChildNeverConfirmsAchievementChanges() {
        final var java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        // Java rejects the internal app argument and exits without ever loading Steam.
        final var previous = System.setProperty(LAUNCHER_PROPERTY, java.toString());
        try {
            final var request = new Request(10, "76561198000000000", List.of("A"),
                    Map.of("A", false), Map.of("A", true));
            final var response = new NativeSteam().execute(request);
            assertEquals(Status.UNKNOWN, response.status());
            assertTrue(response.achievements().isEmpty());
            assertEquals(ErrorCode.STEAM_STORAGE, response.errorCode());
        } finally {
            restore(previous);
        }
    }

    @Test
    public void developmentDispatchesWorkerModeFromAnotherDirectory(final @TempDir Path directory) {
        final var previous = System.clearProperty(LAUNCHER_PROPERTY);
        try {
            final var workingDirectory = Files.createDirectory(directory.resolve("unrelated cwd with spaces"));
            final var output = workingDirectory.resolve("probe.log");
            final var command = WorkerLauncher.command();
            command.add(NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT);
            final var builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(output.toFile());
            try (final var process = new WorkerProcess(builder)) {
                assertTrue(process.await(30));
                assertEquals(0, process.exitValue(), () -> "Worker probe failed; see " + output);
            }
            assertTrue(Files.readString(output).contains("no Steam session opened"));
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        } finally {
            restore(previous);
        }
    }

    private void restore(final String previous) {
        if (previous == null) {
            System.clearProperty(LAUNCHER_PROPERTY);
        } else {
            System.setProperty(LAUNCHER_PROPERTY, previous);
        }
    }
}
