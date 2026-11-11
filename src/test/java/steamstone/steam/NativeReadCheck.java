package steamstone.steam;

import steamstone.steam.nativeapi.*;

import java.util.*;

/**
 * Explicit optional live native read. Never sends setters or StoreStats.
 */
public final class NativeReadCheck {
    public static void main(String[] args) throws Exception {
        String account = System.getenv("STEAMSTONE_TEST_PROFILE");
        var result = new NativeSteam().execute(new NativeProtocol.Request(1182900, account,
                List.of("ACH_01", "ACH_02"), Map.of(), Map.of()));
        System.out.println("Native read: " + result.status() + "; entries=" + result.achievements().size()
                + "; descriptions=" +
                result.achievements().values().stream().filter(a -> !a.description().isBlank()).count());
        if (result.status() != NativeProtocol.Status.READ)
            throw new IllegalStateException(result.message());
        if (result.achievements().values().stream()
                .anyMatch(a -> a.state() == steamstone.model.Achievement.State.UNKNOWN))
            throw new IllegalStateException("Native states unavailable.");
    }
}
