package steamstone.steam;

import java.util.List;
import java.util.Map;

import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.NativeSteam;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Status;

/**
 * Explicit optional live native read. Never sends setters or StoreStats.
 */
public final class NativeReadCheck {
    public static void main(String[] args) throws Exception {
        String account = System.getenv("STEAMSTONE_TEST_PROFILE");
        var result = new NativeSteam().execute(new Request(1182900, account,
                List.of("ACH_01", "ACH_02"), Map.of(), Map.of()));
        System.out.println("Native read: " + result.status() + "; entries=" + result.achievements().size()
                + "; descriptions=" +
                result.achievements().values().stream().filter(a -> !a.description().isBlank()).count());
        if (result.status() != Status.READ)
            throw new IllegalStateException(result.message());
        if (result.achievements().values().stream()
                .anyMatch(a -> a.state() == AchievementState.UNKNOWN))
            throw new IllegalStateException("Native states unavailable.");
    }
}
