package steamstone.steam.nativeapi.worker;

import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUserStatsCallback;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class StorageCallback implements SteamUserStatsCallback {
    private final long appId;
    private boolean waiting;
    private SteamResult result;

    public void begin() {
        result = null;
        waiting = true;
    }

    public void end() {
        waiting = false;
    }

    public SteamResult result() {
        return result;
    }

    @Override
    public void onUserStatsStored(final long gameId, final SteamResult result) {
        if (isAwaitedStorageCallback(gameId))
            this.result = result;
    }

    private boolean isAwaitedStorageCallback(final long gameId) {
        return waiting && result == null && gameId == appId;
    }
}
