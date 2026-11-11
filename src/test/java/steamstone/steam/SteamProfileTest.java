package steamstone.steam;

import org.junit.jupiter.api.Test;
import steamstone.exception.StoneException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SteamProfileTest {
    @Test
    public void acceptsExplicitSteamIdsAndProfileLinks() {
        assertEquals(new SteamProfile("76561198000000000", false), SteamProfile.parse(" 76561198000000000 "));
        assertEquals(new SteamProfile("76561198000000000", false),
                SteamProfile.parse("https://steamcommunity.com/profiles/76561198000000000/?l=english"));
        assertEquals(new SteamProfile("example-person", true),
                SteamProfile.parse("steamcommunity.com/id/example-person/"));
        assertEquals(new SteamProfile("example-person", true), SteamProfile.parse("example-person"));
    }

    @Test
    public void rejectsOtherHostsAndUnexpectedPaths() {
        for (final var value : new String[]{"https://example.com/id/user", "https://steamcommunity.com.evil" +
                ".test/id/user",
                "https://steamcommunity.com@evil.test/id/user", "https://steamcommunity.com/id/user/extra",
                "http://steamcommunity.com/id/user", "https://steamcommunity.com:1234/id/user", "12345", ""}) {
            assertThrows(StoneException.class, () -> SteamProfile.parse(value), value);
        }
    }
}
