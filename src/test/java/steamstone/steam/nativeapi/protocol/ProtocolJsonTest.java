package steamstone.steam.nativeapi.protocol;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import steamstone.exception.StoneException;
import steamstone.model.achievement.AchievementState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ProtocolJsonTest {
    private static final String ACCOUNT = "76561198000000000";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    public void requestRetainsItsWirePropertiesAndValueEquality() {
        final var request = new Request(10, ACCOUNT, List.of("FIRST"),
                Map.of("FIRST", false), Map.of("FIRST", true));
        assertEquals(request, roundTrip(request, Request.class));
        assertEquals(request.hashCode(), roundTrip(request, Request.class).hashCode());
        final var fields = new ArrayList<String>();
        json.valueToTree(request).fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("appId", "steamId", "ids", "expected", "changes"), Set.copyOf(fields));
    }

    @Test
    public void responseRetainsNestedAchievementEntries() {
        final var entry = new Entry(AchievementState.UNLOCKED, "Complete the first chapter.", 12345);
        final var response = new Response(10, ACCOUNT, Status.CONFIRMED, "Saved.", Map.of("FIRST", entry));
        final var restored = roundTrip(response, Response.class);
        assertEquals(response, restored);
        assertEquals(entry, restored.achievements().get("FIRST"));
        assertEquals(12345, restored.achievements().get("FIRST").unlockTime());
        assertThrows(UnsupportedOperationException.class, () -> restored.achievements().clear());
    }

    @Test
    public void jsonDeserializationStillRunsRequestValidation() {
        final var invalid = """
                {"appId":0,"steamId":"76561198000000000","ids":["FIRST"],"expected":{},"changes":{}}
                """;
        final var failure = assertThrows(JsonMappingException.class, () -> json.readValue(invalid, Request.class));
        assertInstanceOf(StoneException.class, failure.getCause());
    }

    @Test
    public void requestStillDefensivelyCopiesItsCollections() {
        final var ids = new ArrayList<>(List.of("FIRST"));
        final var expected = new HashMap<>(Map.of("FIRST", false));
        final var changes = new HashMap<>(Map.of("FIRST", true));
        final var request = new Request(10, ACCOUNT, ids, expected, changes);
        ids.clear();
        expected.clear();
        changes.clear();
        assertEquals(List.of("FIRST"), request.ids());
        assertEquals(Map.of("FIRST", false), request.expected());
        assertEquals(Map.of("FIRST", true), request.changes());
        assertThrows(UnsupportedOperationException.class, () -> request.ids().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.expected().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.changes().clear());
    }

    private <T> T roundTrip(final T value, final Class<T> type) {
        try {
            return json.readValue(json.writeValueAsBytes(value), type);
        } catch (final IOException exception) {
            throw new StoneException("Could not round-trip the worker protocol fixture.", exception);
        }
    }
}
