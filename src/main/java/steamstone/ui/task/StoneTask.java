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
        } catch (final StoneException e) {
            throw new StoneException(e.getMessage(), e);
        } catch (final RuntimeException e) {
            throw new StoneException("The background operation failed.", e);
        }
    }
}
