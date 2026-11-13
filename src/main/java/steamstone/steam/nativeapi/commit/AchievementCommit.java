package steamstone.steam.nativeapi.commit;

import java.util.LinkedHashMap;
import java.util.Map;

import steamstone.exception.StoneException;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.protocol.Entry;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Response;
import steamstone.steam.nativeapi.protocol.Status;

/**
 * No rollback promise: Steam may persist accepted setters even during shutdown.
 */
public final class AchievementCommit {

    public Response execute(final Request request, final AchievementStats stats) {
        final var before = readAll(request, stats);
        if (request.changes().isEmpty())
            return response(request, Status.READ, "", before);
        for (final var change : request.changes().entrySet()) {
            final var entry = before.get(change.getKey());
            if (entry.state() == AchievementState.UNKNOWN) {
                return response(request, Status.REJECTED,
                        "Steam could not read a requested achievement. Reload before editing.", before);
            }
            if ((entry.state() == AchievementState.UNLOCKED) != request.expected().get(change.getKey())) {
                return response(request, Status.CONFLICT,
                        "Steam's state changed since loading. Review the refreshed states and try again.", before);
            }
        }
        final var outcomes = request.changes().entrySet().stream()
                .map((final var change) -> stats.set(change.getKey(), change.getValue())).toList();
        final var accepted = outcomes.stream().filter(Boolean::booleanValue).count();
        final var rejected = outcomes.size() - accepted;
        if (accepted == 0)
            return response(request, Status.REJECTED, "Steam rejected the changes. No achievement was confirmed saved.",
                    before);
        final boolean stored;
        try {
            stored = stats.storeAndWait();
        } catch (final RuntimeException e) {
            throw StoneException.wrap("Steam did not confirm storage. Some changes may still sync. "
                    + "Reload before retrying.", e);
        }
        if (!stored)
            return response(request, Status.UNKNOWN,
                    "Steam rejected storage. Some changes may still sync on exit. Reload before retrying.", before);
        final var after = readAll(request, stats);
        final var matching = request.changes().entrySet().stream().filter((final var change) -> {
            final var entry = after.get(change.getKey());
            return entry.state() != AchievementState.UNKNOWN
                    && (entry.state() == AchievementState.UNLOCKED) == change.getValue();
        }).count();
        final var complete = rejected == 0 && matching == request.changes().size();
        return response(request, complete ? Status.CONFIRMED : Status.PARTIAL,
                complete ? "Saved " + matching + " achievement change(s) to Steam."
                        : "Steam confirmed storage; " + matching + " of " + request.changes().size()
                        + " requested states matched readback. Unmatched changes remain pending.", after);
    }

    private Map<String, Entry> readAll(final Request request, final AchievementStats stats) {
        final var entries = new LinkedHashMap<String, Entry>();
        for (final var id : request.ids())
            entries.put(id, stats.read(id));
        return entries;
    }

    private Response response(final Request request, final Status status, final String message,
                              final Map<String, Entry> entries) {
        return new Response(request.appId(), request.steamId(), status, message, entries);
    }
}
