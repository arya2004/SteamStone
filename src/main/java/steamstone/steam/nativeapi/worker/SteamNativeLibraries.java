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
    private static final String OS_NAME_PROPERTY = "os.name";

    private static final String NATIVE_DIRECTORY = "native";
    private static final String OS_ARCH_PROPERTY = "os.arch";
    private static final String MAC_OS_FRAGMENT = "mac";
    private static final String WINDOWS_OS_FRAGMENT = "win";
    private static final String LINUX_OS_FRAGMENT = "linux";
    private static final String LIBRARY_PREFIX = "lib";
    private static final String MAC_LIBRARY_EXTENSION = ".dylib";
    private static final String WINDOWS_LIBRARY_EXTENSION = ".dll";
    private static final String LINUX_LIBRARY_EXTENSION = ".so";
    private static final String ARCH_64_MARKER = "64";
    private static final String EMPTY_SUFFIX = "";
    private static final String AMD64_ARCH = "amd64";
    private static final String X86_64_ARCH = "x86_64";

    private final Path directory;

    public SteamNativeLibraries() {
        try {
            final var application = Path.of(SteamNativeLibraries.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            // Development: target/classes -> target/native. Installed: app/steamstone.jar -> app/native.
            directory = application.getParent().resolve(NATIVE_DIRECTORY).toAbsolutePath().normalize();
        } catch (final URISyntaxException ex) {
            throw new StoneException("Could not locate the installed Steam libraries.", ex);
        }
    }

    public Path path(final String name) {
        final var os = System.getProperty(OS_NAME_PROPERTY).toLowerCase(Locale.ROOT);
        final var arch = System.getProperty(OS_ARCH_PROPERTY);
        final String file;
        if (os.contains(MAC_OS_FRAGMENT))
            file = LIBRARY_PREFIX + name + MAC_LIBRARY_EXTENSION;
        else if (os.contains(WINDOWS_OS_FRAGMENT))
            file = name + (arch.contains(ARCH_64_MARKER) ? ARCH_64_MARKER : EMPTY_SUFFIX) + WINDOWS_LIBRARY_EXTENSION;
        else if (os.contains(LINUX_OS_FRAGMENT) && (arch.equals(AMD64_ARCH) || arch.equals(X86_64_ARCH)))
            file = LIBRARY_PREFIX + name + LINUX_LIBRARY_EXTENSION;
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
