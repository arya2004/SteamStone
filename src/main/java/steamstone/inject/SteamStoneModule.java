package steamstone.inject;

import steamstone.steam.SteamConstants;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import jakarta.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;

import steamstone.steam.SteamWebApi;
import steamstone.steam.nativeapi.NativeSteam;
import steamstone.ui.achievement.AchievementEditor;
import steamstone.ui.library.LibraryView;

/**
 * Application services are shared; UI instances are created on the JavaFX thread when requested.
 */
public final class SteamStoneModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SteamWebApi.class).in(Singleton.class);
        bind(NativeSteam.class).in(Singleton.class);
        bind(LibraryView.class).in(Singleton.class);
        bind(AchievementEditor.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(SteamConstants.API_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Provides
    public URI steamApiBase() {
        return URI.create(SteamConstants.API_BASE_URI);
    }
}
