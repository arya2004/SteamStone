package steamstone.steam.nativeapi;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.codedisaster.steamworks.SteamAPI;
import java.nio.file.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static steamstone.steam.nativeapi.NativeProtocol.*;

/** Bounded subprocess calls; temp files contain context/edits, never the API key. */
public final class NativeSteam {
    private final ObjectMapper json = new ObjectMapper();
    public Response execute(Request request) throws Exception {
        Path directory = Files.createTempDirectory("steamstone-session-");
        Process process = null;
        try {
            Path input = directory.resolve("request.json"), output = directory.resolve("response.json");
            json.writeValue(input.toFile(), request);
            Files.writeString(directory.resolve("steam_appid.txt"), Long.toString(request.appId()));
            var builder = new ProcessBuilder(command(input, output));
            builder.directory(directory.toFile());
            builder.environment().put("SteamAppId", Long.toString(request.appId()));
            builder.environment().put("SteamGameId", Long.toString(request.appId()));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            if (!process.waitFor(50, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
                return new Response(request.appId(), request.steamId(), Status.UNKNOWN,
                        "Steam session timed out. Changes may still sync; reload before retrying.", Map.of());
            }
            if (!Files.isRegularFile(output) || Files.size(output) > 8_000_000) {
                throw new IllegalStateException("Steam worker exited without a valid result. Reload before retrying; changes may still sync.");
            }
            Response result = json.readValue(output.toFile(), Response.class);
            if (result.appId() != request.appId() || !result.steamId().equals(request.steamId())) {
                throw new IllegalStateException("Steam worker returned a different account/game context.");
            }
            return result;
        } finally {
            if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private List<String> command(Path input, Path output) throws Exception {
        var command = new ArrayList<String>();
        // jpackage supplies a second launcher using the bundled runtime (no system JDK required).
        Path packaged = Path.of(System.getProperty("java.home")).resolve("../../../MacOS/SteamStoneWorker").normalize();
        if (Files.isExecutable(packaged)) command.add(packaged.toString());
        else {
            String exe = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            Path java = Path.of(System.getProperty("java.home"), "bin", exe);
            if (!Files.isExecutable(java)) throw new IllegalStateException("Bundled Steam worker launcher is missing.");
            command.add(java.toString());
            command.add("--enable-native-access=ALL-UNNAMED");
            command.add("-cp");
            var classpath = new LinkedHashSet<String>();
            for (Class<?> type : List.of(NativeWorker.class, ObjectMapper.class, JsonNode.class,
                    JsonFactory.class, JsonCreator.class, SteamAPI.class)) {
                classpath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
            }
            command.add(String.join(File.pathSeparator, classpath));
            command.add(NativeWorker.class.getName());
        }
        command.add(input.toString());
        command.add(output.toString());
        return command;
    }
}
