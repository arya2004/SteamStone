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
    private final ObjectMapper json = new ObjectMapper();

    public Response execute(final Request request) {
        try (final var files = new WorkerFiles("steamstone-session-")) {
            final var input = files.directory().resolve("request.json");
            final var output = files.directory().resolve("response.json");
            json.writeValue(input.toFile(), request);
            Files.writeString(files.directory().resolve("steam_appid.txt"), Long.toString(request.appId()));
            final var command = workerCommand();
            command.add(input.toString());
            command.add(output.toString());
            final var builder = new ProcessBuilder(command).directory(files.directory().toFile());
            // Linux jpackage's re-exec marker belongs to the parent launcher only.
            builder.environment().remove("_JPACKAGE_LAUNCHER");
            builder.environment().put("SteamAppId", Long.toString(request.appId()));
            builder.environment().put("SteamGameId", Long.toString(request.appId()));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            try (final var process = new WorkerProcess(builder)) {
                if (!process.await(50)) {
                    return new Response(request.appId(), request.steamId(), Status.UNKNOWN,
                            "Steam session timed out. Changes may still sync; reload before retrying.", Map.of());
                }
                if (!Files.isRegularFile(output) || Files.size(output) > 8_000_000) {
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
        try (final var files = new WorkerFiles("steamstone-runtime-check-")) {
            final var command = workerCommand();
            command.add("--check-runtime");
            final var builder = new ProcessBuilder(command).directory(files.directory().toFile()).inheritIO();
            builder.environment().remove("_JPACKAGE_LAUNCHER");
            try (final var process = new WorkerProcess(builder)) {
                if (!process.await(30)) {
                    throw new StoneException("Bundled worker check timed out.");
                }
                if (process.exitValue() != 0) {
                    throw new StoneException("Bundled Steam worker check failed.");
                }
            }
        }
    }

    private List<String> workerCommand() {
        final var command = new ArrayList<String>();
        final var os = System.getProperty("os.name");
        final var worker = os.startsWith("Mac") ? "../../../MacOS/SteamStoneWorker"
                : os.startsWith("Windows") ? "../SteamStoneWorker.exe" : "../../bin/SteamStoneWorker";
        final var packaged = Path.of(System.getProperty("java.home")).resolve(worker).normalize();
        if (Files.isExecutable(packaged)) {
            command.add(packaged.toString());
        } else {
            final var executable = os.startsWith("Windows") ? "java.exe" : "java";
            final var java = Path.of(System.getProperty("java.home"), "bin", executable);
            if (!Files.isExecutable(java)) {
                throw new StoneException("Bundled Steam worker launcher is missing.");
            }
            command.add(java.toString());
            command.add("--enable-native-access=ALL-UNNAMED");
            command.add("-cp");
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
