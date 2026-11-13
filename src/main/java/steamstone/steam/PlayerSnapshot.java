package steamstone.steam;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Value;
import lombok.experimental.Accessors;
import steamstone.exception.ErrorCode;

/**
 * Optional player data and a typed reason when achievement states are unavailable.
 */
@Value
@Accessors(fluent = true)
public final class PlayerSnapshot {
    private final JsonNode player;
    private final ErrorCode errorCode;

}
