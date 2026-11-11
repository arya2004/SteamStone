package steamstone.steam.nativeapi;

import com.codedisaster.steamworks.SteamAPI;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import steamstone.exception.StoneException;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Response;
import steamstone.steam.nativeapi.protocol.Status;
import steamstone.steam.nativeapi.worker.NativeWorker;

/**
 * Bounded subprocess calls; temp files contain context/edits, never the API key.
 */
public final class NativeSteam {
    private static final String SESSION_TEMP_PREFIX = "steamstone-session-";
    private static final String RUNTIME_CHECK_TEMP_PREFIX = "steamstone-runtime-check-";
    private static final String REQUEST_FILE_NAME = "request.json";
    private static final String RESPONSE_FILE_NAME = "response.json";
    private static final String APP_ID_FILE_NAME = "steam_appid.txt";
    private static final String JPACKAGE_LAUNCHER_ENV = "_JPACKAGE_LAUNCHER";
    private static final String STEAM_APP_ID_ENV = "SteamAppId";
    private static final String STEAM_GAME_ID_ENV = "SteamGameId";
    private static final String MAC_OS_PREFIX = "Mac";
    private static final String WINDOWS_OS_PREFIX = "Windows";
    private static final String MAC_WORKER_PATH = "../../../MacOS/SteamStoneWorker";
    private static final String WINDOWS_WORKER_PATH = "../SteamStoneWorker.exe";
    private static final String UNIX_WORKER_PATH = "../../bin/SteamStoneWorker";
    private static final String WINDOWS_JAVA_EXECUTABLE = "java.exe";
    private static final String UNIX_JAVA_EXECUTABLE = "java";
    private static final String JAVA_BIN_DIRECTORY = "bin";
    private static final String ENABLE_NATIVE_ACCESS_OPTION = "--enable-native-access=ALL-UNNAMED";
    private static final String CLASSPATH_OPTION = "-cp";
    private static final long SESSION_TIMEOUT_SECONDS = 50;
    private static final long RUNTIME_CHECK_TIMEOUT_SECONDS = 30;
    private static final long MAX_RESPONSE_BYTES = 8_000_000;
    private static final int SUCCESS_EXIT_CODE = 0;

    private final ObjectMapper json = new ObjectMapper();

    public Response execute(final Request request) {
        try (final var files = new WorkerFiles(SESSION_TEMP_PREFIX)) {
            final var input = files.directory().resolve(REQUEST_FILE_NAME);
            final var output = files.directory().resolve(RESPONSE_FILE_NAME);
            json.writeValue(input.toFile(), request);
            Files.writeString(files.directory().resolve(APP_ID_FILE_NAME), Long.toString(request.appId()));
            final var command = workerCommand();
            command.add(input.toString());
            command.add(output.toString());
            final var builder = new ProcessBuilder(command).directory(files.directory().toFile());
            // Linux jpackage's re-exec marker belongs to the parent launcher only.
            builder.environment().remove(JPACKAGE_LAUNCHER_ENV);
            builder.environment().put(STEAM_APP_ID_ENV, Long.toString(request.appId()));
            builder.environment().put(STEAM_GAME_ID_ENV, Long.toString(request.appId()));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            try (final var process = new WorkerProcess(builder)) {
                if (!process.await(SESSION_TIMEOUT_SECONDS)) {
                    return new Response(request.appId(), request.steamId(), Status.UNKNOWN,
                            "Steam session timed out. Changes may still sync; reload before retrying.", Map.of());
                }
                if (!Files.isRegularFile(output) || Files.size(output) > MAX_RESPONSE_BYTES) {
                    throw new StoneException("Steam worker exited without a valid result. Reload before retrying; "
                            + "changes may still sync.");
                }
                final var result = json.readValue(output.toFile(), Response.class);
                if (result.appId() != request.appId() || !result.steamId().equals(request.steamId())) {
                    throw new StoneException("Steam worker returned a different account/game context.");
                }
                return result;
            }
        } catch (final IOException exception) {
            throw new StoneException("Could not exchange the Steam worker result. Changes may still sync; reload "
                    + "before retrying.", exception);
        }
    }

    /**
     * Packaging probe: loads the child launcher without opening a Steam session.
     */
    public void checkRuntime() {
        try (final var files = new WorkerFiles(RUNTIME_CHECK_TEMP_PREFIX)) {
            final var command = workerCommand();
            command.add(NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT);
            final var builder = new ProcessBuilder(command).directory(files.directory().toFile()).inheritIO();
            builder.environment().remove(JPACKAGE_LAUNCHER_ENV);
            try (final var process = new WorkerProcess(builder)) {
                if (!process.await(RUNTIME_CHECK_TIMEOUT_SECONDS)) {
                    throw new StoneException("Bundled worker check timed out.");
                }
                if (process.exitValue() != SUCCESS_EXIT_CODE) {
                    throw new StoneException("Bundled Steam worker check failed.");
                }
            }
        }
    }

    private List<String> workerCommand() {
        final var command = new ArrayList<String>();
        final var os = System.getProperty(NativeRuntimeConstants.OS_NAME_PROPERTY);
        final var worker = os.startsWith(MAC_OS_PREFIX) ? MAC_WORKER_PATH
                : os.startsWith(WINDOWS_OS_PREFIX) ? WINDOWS_WORKER_PATH : UNIX_WORKER_PATH;
        final var packaged =
                Path.of(System.getProperty(NativeRuntimeConstants.JAVA_HOME_PROPERTY)).resolve(worker).normalize();
        if (Files.isExecutable(packaged)) {
            command.add(packaged.toString());
        } else {
            final var executable = os.startsWith(WINDOWS_OS_PREFIX) ? WINDOWS_JAVA_EXECUTABLE : UNIX_JAVA_EXECUTABLE;
            final var java = Path.of(System.getProperty(NativeRuntimeConstants.JAVA_HOME_PROPERTY), JAVA_BIN_DIRECTORY,
                    executable);
            if (!Files.isExecutable(java)) {
                throw new StoneException("Bundled Steam worker launcher is missing.");
            }
            command.add(java.toString());
            command.add(ENABLE_NATIVE_ACCESS_OPTION);
            command.add(CLASSPATH_OPTION);
            final var classpath = new LinkedHashSet<String>();
            for (final var type : List.of(NativeWorker.class, ObjectMapper.class, JsonNode.class,
                    JsonFactory.class, JsonCreator.class, SteamAPI.class, Inject.class)) {
                try {
                    classpath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
                } catch (final URISyntaxException exception) {
                    throw new StoneException("Could not locate the Steam worker's runtime classpath.", exception);
                }
            }
            command.add(String.join(File.pathSeparator, classpath));
            command.add(NativeWorker.class.getName());
        }
        return command;
    }
}
