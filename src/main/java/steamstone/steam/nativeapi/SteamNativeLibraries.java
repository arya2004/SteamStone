package steamstone.steam.nativeapi;

import com.codedisaster.steamworks.SteamLibraryLoader;
import java.nio.file.*;
import java.io.IOException;
import java.util.Locale;

/** Extract only the pinned dependency's matching binaries into this worker's private directory. */
final class SteamNativeLibraries implements SteamLibraryLoader {
    private final Path directory;
    SteamNativeLibraries(Path directory) { this.directory = directory; }
    Path path(String name) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch");
        String file;
        if (os.contains("mac")) file = "lib" + name + ".dylib";
        else if (os.contains("win")) file = name + (arch.contains("64") ? "64" : "") + ".dll";
        else if (os.contains("linux") && (arch.equals("amd64") || arch.equals("x86_64"))) file = "lib" + name + ".so";
        else throw new IllegalStateException("No bundled Steam native runtime for this OS/architecture.");
        return directory.resolve(file);
    }
    @Override public boolean loadLibrary(String name) {
        Path destination = path(name);
        try (var input = SteamLibraryLoader.class.getResourceAsStream("/" + destination.getFileName())) {
            if (input == null) throw new IOException("Missing bundled Steam library.");
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            System.load(destination.toAbsolutePath().toString());
            return true;
        } catch (IOException ex) { throw new IllegalStateException("Could not extract the bundled Steam runtime.", ex); }
    }
}
