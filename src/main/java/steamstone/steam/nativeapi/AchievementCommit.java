package steamstone.steam.nativeapi;

import steamstone.model.Achievement;

import java.util.LinkedHashMap;
import java.util.Map;

import static steamstone.steam.nativeapi.NativeProtocol.*;

/**
 * No rollback promise: Steam may persist accepted setters even during shutdown.
 */
public final class AchievementCommit {
    public interface Stats {
        Entry read(String id);

        boolean set(String id, boolean unlocked);

        boolean storeAndWait() throws Exception;
    }

    public Response execute(Request request, Stats stats) throws Exception {
        Map<String, Entry> before = readAll(request, stats);
        if (request.changes().isEmpty())
            return response(request, Status.READ, "", before);
        for (var change : request.changes().entrySet()) {
            var entry = before.get(change.getKey());
            if (entry.state() == Achievement.State.UNKNOWN) {
                return response(request, Status.REJECTED,
                        "Steam could not read a requested achievement. Reload before editing.", before);
            }
            if ((entry.state() == Achievement.State.UNLOCKED) != request.expected().get(change.getKey())) {
                return response(request, Status.CONFLICT,
                        "Steam's state changed since loading. Review the refreshed states and try again.", before);
            }
        }
        int accepted = 0, rejected = 0;
        for (var change : request.changes().entrySet()) {
            if (stats.set(change.getKey(), change.getValue()))
                accepted++;
            else
                rejected++;
        }
        if (accepted == 0)
            return response(request, Status.REJECTED, "Steam rejected the changes. No achievement was confirmed saved.",
                    before);
        boolean stored;
        try {
            stored = stats.storeAndWait();
        } catch (Exception ex) {
            return response(request, Status.UNKNOWN,
                    "Steam did not confirm storage. Some changes may still sync. Reload before retrying.", before);
        }
        if (!stored)
            return response(request, Status.UNKNOWN,
                    "Steam rejected storage. Some changes may still sync on exit. Reload before retrying.", before);
        Map<String, Entry> after = readAll(request, stats);
        long matching = request.changes().entrySet().stream().filter(change -> {
            var entry = after.get(change.getKey());
            return entry.state() != Achievement.State.UNKNOWN
                    && (entry.state() == Achievement.State.UNLOCKED) == change.getValue();
        }).count();
        boolean complete = rejected == 0 && matching == request.changes().size();
        return response(request, complete ? Status.CONFIRMED : Status.PARTIAL,
                complete ? "Saved " + matching + " achievement change(s) to Steam."
                        : "Steam confirmed storage; " + matching + " of " + request.changes().size()
                        + " requested states matched readback. Unmatched changes remain pending.", after);
    }

    private Map<String, Entry> readAll(Request request, Stats stats) {
        var entries = new LinkedHashMap<String, Entry>();
        for (String id : request.ids())
            entries.put(id, stats.read(id));
        return entries;
    }

    private Response response(Request request, Status status, String message, Map<String, Entry> entries) {
        return new Response(request.appId(), request.steamId(), status, message, entries);
    }
}
