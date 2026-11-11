package steamstone.steam.nativeapi;

import com.codedisaster.steamworks.SteamLibraryLoader;

import java.nio.file.*;
import java.util.Locale;

/**
 * Load native files prepared at build/install time; never extract libraries at application startup.
 */
final class SteamNativeLibraries implements SteamLibraryLoader {
    private final Path directory;

    SteamNativeLibraries() {
        try {
            Path application = Path.of(SteamNativeLibraries.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            // Development: target/classes -> target/native. Installed: app/steamstone.jar -> app/native.
            directory = application.getParent().resolve("native");
        } catch (java.net.URISyntaxException ex) {
            throw new IllegalStateException("Could not locate the installed Steam libraries.", ex);
        }
    }

    Path path(String name) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch");
        String file;
        if (os.contains("mac"))
            file = "lib" + name + ".dylib";
        else if (os.contains("win"))
            file = name + (arch.contains("64") ? "64" : "") + ".dll";
        else if (os.contains("linux") && (arch.equals("amd64") || arch.equals("x86_64")))
            file = "lib" + name + ".so";
        else
            throw new IllegalStateException("No bundled Steam native runtime for this OS/architecture.");
        return directory.resolve(file);
    }

    @Override
    public boolean loadLibrary(String name) {
        Path destination = path(name);
        if (!Files.isRegularFile(destination)) {
            throw new IllegalStateException(
                    "Steam native libraries are missing. Rebuild the project or reinstall SteamStone.");
        }
        System.load(destination.toAbsolutePath().toString());
        return true;
    }
}
