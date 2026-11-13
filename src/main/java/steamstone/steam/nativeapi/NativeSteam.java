package steamstone.steam.nativeapi;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Response;
import steamstone.steam.nativeapi.protocol.Status;

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
            final var command = WorkerLauncher.command();
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
                if (!process.await(SESSION_TIMEOUT_SECONDS) || process.exitValue() != SUCCESS_EXIT_CODE
                        || !Files.isRegularFile(output)) {
                    return new Response(request.appId(), request.steamId(), Status.UNKNOWN,
                            ErrorCode.STEAM_STORAGE, Map.of());
                }
                if (Files.size(output) > MAX_RESPONSE_BYTES) {
                    throw new StoneException(ErrorCode.STEAM_STORAGE);
                }
                final var result = json.readValue(output.toFile(), Response.class);
                if (result.appId() != request.appId() || !result.steamId().equals(request.steamId())) {
                    throw new StoneException(ErrorCode.STEAM_STORAGE);
                }
                return result;
            }
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.STEAM_STORAGE, e);
        }
    }

    /**
     * Packaging probe: loads the child launcher without opening a Steam session.
     */
    public void checkRuntime() {
        try (final var files = new WorkerFiles(RUNTIME_CHECK_TEMP_PREFIX)) {
            final var command = WorkerLauncher.command();
            command.add(NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT);
            final var builder = new ProcessBuilder(command).directory(files.directory().toFile()).inheritIO();
            builder.environment().remove(JPACKAGE_LAUNCHER_ENV);
            try (final var process = new WorkerProcess(builder)) {
                if (!process.await(RUNTIME_CHECK_TIMEOUT_SECONDS)) {
                    throw new StoneException(ErrorCode.NATIVE_RUNTIME);
                }
                if (process.exitValue() != SUCCESS_EXIT_CODE) {
                    throw new StoneException(ErrorCode.NATIVE_RUNTIME);
                }
            }
        }
    }
}
