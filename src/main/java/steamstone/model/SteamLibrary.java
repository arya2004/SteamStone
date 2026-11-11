package steamstone.model;

import java.time.Instant;
import java.util.List;

public record SteamLibrary(String steamId, String playerName, List<Game> games, Instant fetchedAt) {
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
