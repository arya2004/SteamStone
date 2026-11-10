package steamstone.model;

import java.time.Instant;
import java.util.List;

public record SteamLibrary(String steamId, String playerName, List<Game> games, Instant fetchedAt) {
    public SteamLibrary {
        games = List.copyOf(games);
    }

    public long playtimeMinutes() {
        return games.stream().mapToLong(Game::playtimeMinutes).sum();
    }
}
