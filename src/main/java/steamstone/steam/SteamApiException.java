package steamstone.steam;

/**
 * A credential-free message suitable for presentation in the application.
 */
public final class SteamApiException extends Exception {
    public SteamApiException(String message) {
        super(message);
    }
}
