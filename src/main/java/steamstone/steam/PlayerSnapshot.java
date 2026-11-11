package steamstone.steam;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Optional player data and the notice explaining unavailable achievement states.
 */
@Value
@Accessors(fluent = true)
public final class PlayerSnapshot {
    private final JsonNode player;
    private final String notice;

}
