package steamstone.steam;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Optional player data and the notice explaining unavailable achievement states.
 */
public record PlayerSnapshot(JsonNode player, String notice) {
}
