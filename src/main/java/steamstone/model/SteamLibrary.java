package steamstone.model;

import java.time.Instant;
import java.util.List;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public final class SteamLibrary {
    private final String steamId;
    private final String playerName;
    private final List<Game> games;
    private final Instant fetchedAt;

    public SteamLibrary(final String steamId, final String playerName, final List<Game> games,
                        final Instant fetchedAt) {
        this.steamId = steamId;
        this.playerName = playerName;
        this.games = List.copyOf(games);
        this.fetchedAt = fetchedAt;
    }

    public long playtimeMinutes() {
        return games.stream().mapToLong(Game::playtimeMinutes).sum();
    }
}
