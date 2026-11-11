package steamstone.inject;

import com.google.inject.AbstractModule;

import java.net.URI;

import lombok.RequiredArgsConstructor;

/**
 * Replaces the remote API base with a local HTTP fixture while retaining real application wiring.
 */
@RequiredArgsConstructor
public final class SteamApiFixtureModule extends AbstractModule {
    private final URI base;

    @Override
    protected void configure() {
        bind(URI.class).toInstance(base);
    }
}
