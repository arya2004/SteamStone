package steamstone.inject;

import com.google.inject.AbstractModule;
import steamstone.settings.ConnectionStore;
import lombok.RequiredArgsConstructor;

/**
 * Graphical checks never access the contributor's saved connection; each check uses an isolated OS entry.
 */
@RequiredArgsConstructor
public final class SavedConnectionFixtureModule extends AbstractModule {
    private final ConnectionStore connection;

    @Override
    protected void configure() {
        bind(ConnectionStore.class).toInstance(connection);
    }
}
