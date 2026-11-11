package steamstone.steam.nativeapi.worker;

import com.codedisaster.steamworks.SteamResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StorageCallbackTest {
    @Test
    public void ignoresEventsBeforeStoreAndForOtherGamesAndKeepsFirstMatchingResult() {
        final var callback = new StorageCallback(10);
        callback.onUserStatsStored(10, SteamResult.OK);
        assertNull(callback.result());
        callback.begin();
        callback.onUserStatsStored(20, SteamResult.OK);
        assertNull(callback.result());
        callback.onUserStatsStored(10, SteamResult.Fail);
        callback.onUserStatsStored(10, SteamResult.OK);
        assertEquals(SteamResult.Fail, callback.result());
    }
}
