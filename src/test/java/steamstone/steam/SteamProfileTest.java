package steamstone.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SteamProfileTest {
    @Test
    void acceptsExplicitSteamIdsAndProfileLinks() {
        assertEquals(new SteamProfile("76561198000000000", false), SteamProfile.parse(" 76561198000000000 "));
        assertEquals(new SteamProfile("76561198000000000", false),
                SteamProfile.parse("https://steamcommunity.com/profiles/76561198000000000/?l=english"));
        assertEquals(new SteamProfile("example-person", true),
                SteamProfile.parse("steamcommunity.com/id/example-person/"));
        assertEquals(new SteamProfile("example-person", true), SteamProfile.parse("example-person"));
    }

    @Test
    void rejectsOtherHostsAndUnexpectedPaths() {
        for (String value : new String[]{"https://example.com/id/user", "https://steamcommunity.com.evil.test/id/user",
                "https://steamcommunity.com@evil.test/id/user", "https://steamcommunity.com/id/user/extra",
                "http://steamcommunity.com/id/user", "https://steamcommunity.com:1234/id/user", "12345", ""}) {
            assertThrows(IllegalArgumentException.class, () -> SteamProfile.parse(value), value);
        }
    }
}
