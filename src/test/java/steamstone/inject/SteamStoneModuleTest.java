package steamstone.inject;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import steamstone.steam.SteamWebApi;
import steamstone.steam.nativeapi.NativeSteam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class SteamStoneModuleTest {
    @Test
    public void buildsTheServiceGraphWithoutStartingJavaFxOrSteam() {
        final var injector = SteamStoneInjector.create();
        final var api = injector.getInstance(SteamWebApi.class);
        final var nativeSteam = injector.getInstance(NativeSteam.class);
        assertNotNull(api);
        assertNotNull(nativeSteam);
        assertSame(api, injector.getInstance(SteamWebApi.class));
        assertSame(nativeSteam, injector.getInstance(NativeSteam.class));
    }

    @Test
    public void suppliesASharedHttpClientThatCannotRedirectCredentials() {
        final var injector = SteamStoneInjector.create();
        final var client = injector.getInstance(HttpClient.class);
        assertSame(client, injector.getInstance(HttpClient.class));
        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
        assertEquals(Duration.ofSeconds(10), client.connectTimeout().orElseThrow());
        assertEquals(URI.create("https://api.steampowered.com/"), injector.getInstance(URI.class));
    }
}
