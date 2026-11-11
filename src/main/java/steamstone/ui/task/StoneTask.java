package steamstone.ui.task;

import jakarta.inject.Inject;

import java.util.function.Supplier;

import javafx.concurrent.Task;
import lombok.RequiredArgsConstructor;
import steamstone.exception.StoneException;

/**
 * Runs an application operation through JavaFX's observable task lifecycle.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class StoneTask<T> extends Task<T> {
    private final Supplier<T> operation;

    @Override
    protected T call() {
        try {
            return operation.get();
        } catch (final StoneException exception) {
            throw new StoneException(exception.getMessage(), exception);
        } catch (final RuntimeException exception) {
            throw new StoneException("The background operation failed.", exception);
        }
    }
}
