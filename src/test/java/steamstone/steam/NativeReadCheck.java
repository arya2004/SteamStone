package steamstone.steam;

import java.util.List;
import java.util.Map;

import steamstone.exception.StoneException;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.NativeSteam;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Status;

/**
 * Explicit optional live native read. Never sends setters or StoreStats.
 */
public final class NativeReadCheck {
    public static void main(final String[] args) {
        final var account = System.getenv("STEAMSTONE_TEST_PROFILE");
        final var result = new NativeSteam().execute(new Request(1182900, account,
                List.of("ACH_01", "ACH_02"), Map.of(), Map.of()));
        System.out.println("Native read: " + result.status() + "; entries=" + result.achievements().size()
                + "; descriptions=" +
                result.achievements().values().stream().filter((final var a) -> !a.description().isBlank()).count());
        if (result.status() != Status.READ)
            throw new StoneException(result.message());
        if (result.achievements().values().stream()
                .anyMatch((final var a) -> a.state() == AchievementState.UNKNOWN))
            throw new StoneException("Native states unavailable.");
    }
}
