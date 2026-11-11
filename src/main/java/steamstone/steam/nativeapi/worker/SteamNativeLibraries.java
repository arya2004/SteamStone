package steamstone.steam.nativeapi.worker;

import com.codedisaster.steamworks.SteamLibraryLoader;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import steamstone.exception.StoneException;

/**
 * Load native files prepared at build/install time; never extract libraries at application startup.
 */
public final class SteamNativeLibraries implements SteamLibraryLoader {
    private final Path directory;

    public SteamNativeLibraries() {
        try {
            final var application = Path.of(SteamNativeLibraries.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            // Development: target/classes -> target/native. Installed: app/steamstone.jar -> app/native.
            directory = application.getParent().resolve("native");
        } catch (final URISyntaxException ex) {
            throw new StoneException("Could not locate the installed Steam libraries.", ex);
        }
    }

    public Path path(final String name) {
        final var os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        final var arch = System.getProperty("os.arch");
        final String file;
        if (os.contains("mac"))
            file = "lib" + name + ".dylib";
        else if (os.contains("win"))
            file = name + (arch.contains("64") ? "64" : "") + ".dll";
        else if (os.contains("linux") && (arch.equals("amd64") || arch.equals("x86_64")))
            file = "lib" + name + ".so";
        else
            throw new StoneException("No bundled Steam native runtime for this OS/architecture.");
        return directory.resolve(file);
    }

    @Override
    public boolean loadLibrary(final String name) {
        final var destination = path(name);
        if (!Files.isRegularFile(destination)) {
            throw new StoneException(
                    "Steam native libraries are missing. Rebuild the project or reinstall SteamStone.");
        }
        System.load(destination.toAbsolutePath().toString());
        return true;
    }
}
