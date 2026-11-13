package steamstone.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.KeyringStorageType;
import com.github.javakeyring.PasswordAccessException;
import com.github.javakeyring.BackendNotSupportedException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

import java.util.Optional;

/**
 * Stores the complete saved connection in the operating system credential store.
 */
@RequiredArgsConstructor
public final class ConnectionStore {
    private static final String OS_PROPERTY = "os.name";
    private static final String WINDOWS_PREFIX = "Windows";
    private static final String MAC_PREFIX = "Mac";
    private static final String ACCOUNT = "saved-connection";
    private static final String KEY_PATTERN = "[a-fA-F0-9]{32}";
    private final String service;
    private final ObjectMapper json = new ObjectMapper();

    public synchronized Optional<SavedConnection> saved() {
        try (final var keyring = open()) {
            final var value = keyring.getPassword(service, ACCOUNT);
            if (value == null || value.isBlank())
                return Optional.empty();
            final var saved = json.readValue(value, SavedConnection.class);
            validate(saved);
            return Optional.of(saved);
        } catch (final PasswordAccessException e) {
            // Missing or inaccessible saved credentials require manual login.
            return Optional.empty();
        } catch (final Exception | LinkageError e) {
            throw StoneException.wrap(ErrorCode.SAVED_ACCOUNT, e);
        }
    }

    public synchronized void remember(final String key, final String profile) {
        final var saved = new SavedConnection(key, profile);
        validate(saved);
        try (final var keyring = open()) {
            keyring.setPassword(service, ACCOUNT, json.writeValueAsString(saved));
        } catch (final Exception | LinkageError e) {
            throw StoneException.wrap(ErrorCode.SAVED_ACCOUNT, e);
        }
    }

    public synchronized void forget() {
        try (final var keyring = open()) {
            // Erase the credentials even if deleting the empty entry subsequently fails.
            keyring.setPassword(service, ACCOUNT, "");
            keyring.deletePassword(service, ACCOUNT);
        } catch (final Exception | LinkageError e) {
            throw StoneException.wrap(ErrorCode.SAVED_ACCOUNT, e);
        }
    }

    private Keyring open() {
        final var os = System.getProperty(OS_PROPERTY);
        final var backends = os.startsWith(MAC_PREFIX) ? List.of(KeyringStorageType.LEGACY_OSX_KEYCHAIN)
                : os.startsWith(WINDOWS_PREFIX) ? List.of(KeyringStorageType.WINDOWS_CREDENTIAL_STORE)
                : List.of(KeyringStorageType.GNOME_KEYRING, KeyringStorageType.KWALLET);
        final var failure = new StoneException(ErrorCode.SAVED_ACCOUNT);
        for (final var backend : backends) {
            try {
                return Keyring.create(backend);
            } catch (final BackendNotSupportedException | LinkageError e) {
                failure.addSuppressed(e);
            }
        }
        throw failure;
    }

    private void validate(final SavedConnection saved) {
        if (saved == null || saved.apiKey() == null || !saved.apiKey().matches(KEY_PATTERN)
                || saved.profile() == null || saved.profile().isBlank())
            throw new StoneException(ErrorCode.SAVED_ACCOUNT);
    }
}
