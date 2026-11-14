package steamstone.inject;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

import org.junit.jupiter.api.Test;

import steamstone.steam.SteamWebApi;
import steamstone.steam.nativeapi.NativeSteam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        api.close();
    }

    @Test
    public void suppliesASharedHttpClientThatCannotRedirectCredentials() {
        final var injector = SteamStoneInjector.create();
        final var client = injector.getInstance(HttpClient.class);
        assertSame(client, injector.getInstance(HttpClient.class));
        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
        assertEquals(Duration.ofSeconds(10), client.connectTimeout().orElseThrow());
        assertEquals(URI.create("https://api.steampowered.com/"), injector.getInstance(URI.class));
        client.close();
    }

    @Test
    public void sharesABoundedPoolAndReleasesIdleWorkers() {
        final var injector = SteamStoneInjector.create();
        try (final var executor = injector.getInstance(ThreadPoolExecutor.class)) {
            assertSame(executor, injector.getInstance(ThreadPoolExecutor.class));
            assertEquals(2, executor.getMaximumPoolSize());
            assertEquals(5, executor.getKeepAliveTime(TimeUnit.SECONDS));
            assertTrue(executor.allowsCoreThreadTimeOut());
            assertEquals(0, executor.getPoolSize());
            executor.setKeepAliveTime(50, TimeUnit.MILLISECONDS);
            executor.submit(() -> true).get(2, TimeUnit.SECONDS);
            final var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (executor.getPoolSize() != 0 && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertEquals(0, executor.getPoolSize());
            assertTrue(executor.submit(() -> true).get(2, TimeUnit.SECONDS));
        } catch (final Exception e) {
            throw StoneException.wrap(ErrorCode.APPLICATION, e);
        }
    }

}
