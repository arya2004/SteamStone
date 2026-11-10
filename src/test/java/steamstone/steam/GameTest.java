package steamstone.steam;

import org.junit.jupiter.api.Test;
import steamstone.model.Game;

import static org.junit.jupiter.api.Assertions.*;

class GameTest {
    @Test void imageLocationsAreBuiltOnlyFromValidSteamHashes() {
        assertNull(new Game(10, "Game", 0, 0, "https://untrusted.test/image").iconUrl());
        assertNull(new Game(10, "Game", 0, 0, "").iconUrl());
        assertEquals("https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/10/"
                        + "a".repeat(40) + ".jpg",
                new Game(10, "Game", 0, 0, "a".repeat(40)).iconUrl());
    }
}
