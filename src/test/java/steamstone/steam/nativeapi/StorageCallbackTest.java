package steamstone.steam.nativeapi;
import org.junit.jupiter.api.Test;
import com.codedisaster.steamworks.SteamResult;
import static org.junit.jupiter.api.Assertions.*;
class StorageCallbackTest {
    @Test void ignoresEventsBeforeStoreAndForOtherGamesAndKeepsFirstMatchingResult() {
        var callback = new NativeWorker.StorageCallback(10);
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
