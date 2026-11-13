package steamstone.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ErrorCode {
    NONE(""),
    INVALID_INPUT("Invalid input. Check the supplied details."),
    STEAM_API("Steam API error. Please try again."),
    STEAM_SESSION("Steam session error. Keep Steam running and reload."),
    STEAM_STORAGE("Steam save error. Changes may still sync; reload before retrying."),
    NATIVE_RUNTIME("Steam runtime error. Rebuild or reinstall the app."),
    CANCELLED("Operation cancelled."),
    SAVED_ACCOUNT("Saved account error. Please try again."),
    APPLICATION("Application error. Please try again.");

    private final String message;
}
