package steamstone.steam.nativeapi;

import com.codedisaster.steamworks.SteamAPI;
import com.sun.jna.Native;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import steamstone.Main;
import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

/**
 * Reuses the installed app executable. Development runs use the selected JDK with absolute classpath entries.
 */
public final class WorkerLauncher {
    private static final String PACKAGED_LAUNCHER_PROPERTY = "jpackage.app-path";
    private static final String JAVA_HOME_PROPERTY = "java.home";
    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String WINDOWS_OS_PREFIX = "Windows";
    private static final String WINDOWS_JAVA_EXECUTABLE = "java.exe";
    private static final String UNIX_JAVA_EXECUTABLE = "java";
    private static final String JAVA_BIN_DIRECTORY = "bin";
    private static final String ENABLE_NATIVE_ACCESS_OPTION = "--enable-native-access=ALL-UNNAMED";
    private static final String CLASSPATH_OPTION = "-cp";
    private static final String WORKER_ARGUMENT = "--steam-worker";

    public static List<String> command() {
        final var command = new ArrayList<String>();
        final var installed = System.getProperty(PACKAGED_LAUNCHER_PROPERTY);
        if (installed != null) {
            final var launcher = Path.of(installed);
            if (!launcher.isAbsolute() || !Files.isRegularFile(launcher) || !Files.isExecutable(launcher)) {
                throw new StoneException(ErrorCode.NATIVE_RUNTIME);
            }
            command.add(launcher.normalize().toString());
        } else {
            final var executable = System.getProperty(OS_NAME_PROPERTY).startsWith(WINDOWS_OS_PREFIX)
                    ? WINDOWS_JAVA_EXECUTABLE : UNIX_JAVA_EXECUTABLE;
            final var java = Path.of(System.getProperty(JAVA_HOME_PROPERTY), JAVA_BIN_DIRECTORY, executable);
            if (!java.isAbsolute() || !Files.isExecutable(java)) {
                throw new StoneException(ErrorCode.NATIVE_RUNTIME);
            }
            command.add(java.normalize().toString());
            command.add(ENABLE_NATIVE_ACCESS_OPTION);
            command.add(CLASSPATH_OPTION);
            final var classpath = new LinkedHashSet<String>();
            for (final var type : List.of(Main.class, ObjectMapper.class, JsonNode.class,
                    JsonFactory.class, JsonCreator.class, SteamAPI.class, Inject.class, Native.class)) {
                try {
                    classpath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                            .toAbsolutePath().normalize().toString());
                } catch (final URISyntaxException e) {
                    throw new StoneException(ErrorCode.NATIVE_RUNTIME, e);
                }
            }
            command.add(String.join(File.pathSeparator, classpath));
            command.add(Main.class.getName());
        }
        command.add(WORKER_ARGUMENT);
        return command;
    }
}
