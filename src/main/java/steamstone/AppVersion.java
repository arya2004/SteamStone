package steamstone;

import java.io.IOException;
import java.util.Properties;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

/**
 * Version generated from Maven, shared by the UI and HTTP client.
 */
public final class AppVersion {
    private static final String RESOURCE = "/app.properties";
    private static final String VERSION_KEY = "version";
    public static final String VERSION = load();

    private static String load() {
        try (final var input = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (input == null)
                throw new StoneException(ErrorCode.APPLICATION);
            final var properties = new Properties();
            properties.load(input);
            final var version = properties.getProperty(VERSION_KEY, "");
            if (!version.matches("[0-9]+[.][0-9]+[.][0-9]+"))
                throw new StoneException(ErrorCode.APPLICATION);
            return version;
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }
}
