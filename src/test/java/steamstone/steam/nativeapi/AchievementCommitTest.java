package steamstone.steam.nativeapi;

import org.junit.jupiter.api.Test;
import steamstone.model.Achievement;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static steamstone.steam.nativeapi.NativeProtocol.*;

class AchievementCommitTest {
    private static final String ACCOUNT = "76561198000000000";

    private Request request(Map<String, Boolean> expected, Map<String, Boolean> desired) {
        return new Request(10, ACCOUNT, List.of("A", "B"), expected, desired);
    }

    private static final class FakeSteam implements AchievementCommit.Stats {
        Map<String, Achievement.State> states =
                new HashMap<>(Map.of("A", Achievement.State.LOCKED, "B", Achievement.State.UNLOCKED));
        Set<String> reject = new HashSet<>();
        boolean storageAccepted = true, timeout, ignoreSetters;
        int setters, stores;

        @Override
        public Entry read(String id) {
            return new Entry(states.get(id), "Description", 0);
        }

        @Override
        public boolean set(String id, boolean unlocked) {
            setters++;
            if (reject.contains(id))
                return false;
            if (!ignoreSetters)
                states.put(id, unlocked ? Achievement.State.UNLOCKED : Achievement.State.LOCKED);
            return true;
        }

        @Override
        public boolean storeAndWait() throws Exception {
            stores++;
            if (timeout)
                throw new java.util.concurrent.TimeoutException();
            return storageAccepted;
        }
    }

    @Test
    void readsNeverSetOrStore() throws Exception {
        var steam = new FakeSteam();
        var response = new AchievementCommit().execute(request(Map.of(), Map.of()), steam);
        assertEquals(Status.READ, response.status());
        assertEquals(0, steam.setters);
        assertEquals(0, steam.stores);
        assertEquals(Achievement.State.LOCKED, response.achievements().get("A").state());
    }

    @Test
    void locksAndUnlocksWithSingleConfirmedStoreAndReadback() throws Exception {
        var steam = new FakeSteam();
        var response =
                new AchievementCommit().execute(request(Map.of("A", false, "B", true), Map.of("A", true, "B", false)),
                        steam);
        assertEquals(Status.CONFIRMED, response.status());
        assertEquals(2, steam.setters);
        assertEquals(1, steam.stores);
        assertEquals(Achievement.State.UNLOCKED, response.achievements().get("A").state());
        assertEquals(Achievement.State.LOCKED, response.achievements().get("B").state());
    }

    @Test
    void concurrentChangeStopsEntireBatchBeforeAnySetter() throws Exception {
        var steam = new FakeSteam();
        var response =
                new AchievementCommit().execute(request(Map.of("A", true, "B", true), Map.of("A", false, "B", false)),
                        steam);
        assertEquals(Status.CONFLICT, response.status());
        assertEquals(0, steam.setters);
        assertEquals(0, steam.stores);
    }

    @Test
    void unreadableStateStopsBatchBeforeAnySetter() throws Exception {
        var steam = new FakeSteam();
        steam.states.put("A", Achievement.State.UNKNOWN);
        var response = new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam);
        assertEquals(Status.REJECTED, response.status());
        assertEquals(0, steam.setters);
    }

    @Test
    void rejectedSetterDoesNotDiscardOtherAcceptedChanges() throws Exception {
        var steam = new FakeSteam();
        steam.reject.add("B");
        var response =
                new AchievementCommit().execute(request(Map.of("A", false, "B", true), Map.of("A", true, "B", false)),
                        steam);
        assertEquals(Status.PARTIAL, response.status());
        assertEquals(1, steam.stores);
        assertEquals(Achievement.State.UNLOCKED, response.achievements().get("A").state());
        assertEquals(Achievement.State.UNLOCKED, response.achievements().get("B").state());
    }

    @Test
    void allRejectedDoesNotCallStore() throws Exception {
        var steam = new FakeSteam();
        steam.reject.add("A");
        var response = new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam);
        assertEquals(Status.REJECTED, response.status());
        assertEquals(0, steam.stores);
    }

    @Test
    void timeoutNeverConfirmsLocallyChangedStates() throws Exception {
        var steam = new FakeSteam();
        steam.timeout = true;
        var response = new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam);
        assertEquals(Status.UNKNOWN, response.status());
        assertEquals(Achievement.State.LOCKED, response.achievements().get("A").state());
        assertEquals(Achievement.State.UNLOCKED, steam.states.get("A"));
    }

    @Test
    void rejectedStorageNeverClaimsSuccess() throws Exception {
        var steam = new FakeSteam();
        steam.storageAccepted = false;
        assertEquals(Status.UNKNOWN,
                new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam).status());
    }

    @Test
    void successfulCallbackStillRequiresMatchingReadback() throws Exception {
        var steam = new FakeSteam();
        steam.ignoreSetters = true;
        assertEquals(Status.PARTIAL,
                new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam).status());
    }

    @Test
    void refusesEditsOutsideRequestedIdsOrWithoutBaseline() {
        assertThrows(IllegalArgumentException.class, () -> request(Map.of("X", false), Map.of("X", true)));
        assertThrows(IllegalArgumentException.class, () -> request(Map.of(), Map.of("A", true)));
        assertThrows(IllegalArgumentException.class, () -> new Request(0, ACCOUNT, List.of("A"), Map.of(), Map.of()));
    }
}
