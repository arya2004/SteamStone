package steamstone.steam.nativeapi.commit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import steamstone.exception.StoneException;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementCommitTest {
    private static final String ACCOUNT = "76561198000000000";

    private Request request(Map<String, Boolean> expected, Map<String, Boolean> desired) {
        return new Request(10, ACCOUNT, List.of("A", "B"), expected, desired);
    }

    @Test
    void readsNeverSetOrStore() {
        var steam = new FakeSteam();
        var response = new AchievementCommit().execute(request(Map.of(), Map.of()), steam);
        assertEquals(Status.READ, response.status());
        assertEquals(0, steam.setters);
        assertEquals(0, steam.stores);
        assertEquals(AchievementState.LOCKED, response.achievements().get("A").state());
    }

    @Test
    void locksAndUnlocksWithSingleConfirmedStoreAndReadback() {
        var steam = new FakeSteam();
        var response =
                new AchievementCommit().execute(request(Map.of("A", false, "B", true), Map.of("A", true, "B", false)),
                        steam);
        assertEquals(Status.CONFIRMED, response.status());
        assertEquals(2, steam.setters);
        assertEquals(1, steam.stores);
        assertEquals(AchievementState.UNLOCKED, response.achievements().get("A").state());
        assertEquals(AchievementState.LOCKED, response.achievements().get("B").state());
    }

    @Test
    void concurrentChangeStopsEntireBatchBeforeAnySetter() {
        var steam = new FakeSteam();
        var response =
                new AchievementCommit().execute(request(Map.of("A", true, "B", true), Map.of("A", false, "B", false)),
                        steam);
        assertEquals(Status.CONFLICT, response.status());
        assertEquals(0, steam.setters);
        assertEquals(0, steam.stores);
    }

    @Test
    void unreadableStateStopsBatchBeforeAnySetter() {
        var steam = new FakeSteam();
        steam.states.put("A", AchievementState.UNKNOWN);
        var response = new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam);
        assertEquals(Status.REJECTED, response.status());
        assertEquals(0, steam.setters);
    }

    @Test
    void rejectedSetterDoesNotDiscardOtherAcceptedChanges() {
        var steam = new FakeSteam();
        steam.reject.add("B");
        var response =
                new AchievementCommit().execute(request(Map.of("A", false, "B", true), Map.of("A", true, "B", false)),
                        steam);
        assertEquals(Status.PARTIAL, response.status());
        assertEquals(1, steam.stores);
        assertEquals(AchievementState.UNLOCKED, response.achievements().get("A").state());
        assertEquals(AchievementState.UNLOCKED, response.achievements().get("B").state());
    }

    @Test
    void allRejectedDoesNotCallStore() {
        var steam = new FakeSteam();
        steam.reject.add("A");
        var response = new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam);
        assertEquals(Status.REJECTED, response.status());
        assertEquals(0, steam.stores);
    }

    @Test
    void timeoutNeverConfirmsLocallyChangedStates() {
        var steam = new FakeSteam();
        steam.timeout = true;
        var failure = assertThrows(StoneException.class,
                () -> new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam));
        assertTrue(failure.getMessage().contains("may still sync"));
        assertInstanceOf(StoneException.class, failure.getCause());
        assertInstanceOf(TimeoutException.class, failure.getCause().getCause());
        assertEquals(AchievementState.UNLOCKED, steam.states.get("A"));
    }

    @Test
    void rejectedStorageNeverClaimsSuccess() {
        var steam = new FakeSteam();
        steam.storageAccepted = false;
        assertEquals(Status.UNKNOWN,
                new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam).status());
    }

    @Test
    void successfulCallbackStillRequiresMatchingReadback() {
        var steam = new FakeSteam();
        steam.ignoreSetters = true;
        assertEquals(Status.PARTIAL,
                new AchievementCommit().execute(request(Map.of("A", false), Map.of("A", true)), steam).status());
    }

    @Test
    void refusesEditsOutsideRequestedIdsOrWithoutBaseline() {
        assertThrows(StoneException.class, () -> request(Map.of("X", false), Map.of("X", true)));
        assertThrows(StoneException.class, () -> request(Map.of(), Map.of("A", true)));
        assertThrows(StoneException.class, () -> new Request(0, ACCOUNT, List.of("A"), Map.of(), Map.of()));
    }
}
