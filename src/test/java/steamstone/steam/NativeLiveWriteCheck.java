package steamstone.steam;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import steamstone.model.achievement.Achievement;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.NativeSteam;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Response;
import steamstone.steam.nativeapi.protocol.Status;

/**
 * Explicit opt-in only: one locked Requiem achievement, then restore the complete baseline.
 */
public final class NativeLiveWriteCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("--unlock-and-restore-one-locked-requiem-achievement"))
            throw new IllegalArgumentException("Explicit live-write opt-in required.");
        String account = System.getenv("STEAMSTONE_TEST_PROFILE");
        String keyFile = System.getenv("STEAMSTONE_TEST_KEY_FILE");
        var matcher = Pattern.compile("(?<![a-fA-F0-9])[a-fA-F0-9]{32}(?![a-fA-F0-9])")
                .matcher(Files.readString(Path.of(keyFile)));
        if (!matcher.find())
            throw new IllegalStateException("No test API key found.");
        var web = new SteamWebApi().loadAchievements(matcher.group(), account, 1182900);
        var ids = web.achievements().stream().map(Achievement::id).toList();
        if (ids.isEmpty())
            throw new IllegalStateException("No achievement identifiers returned.");
        var steam = new NativeSteam();
        var json = new ObjectMapper();
        Path output = Path.of("target", "verification");
        Files.createDirectories(output);
        var baseline = read(steam, account, ids);
        requireKnown(baseline);
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("requiem-before.json").toFile(), baseline);
        String chosen = ids.stream().filter(id -> baseline.achievements().get(id).state() == AchievementState.LOCKED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No locked achievements; no write performed."));
        var recovery = new Request(1182900, account, ids, Map.of(chosen, true), Map.of(chosen, false));
        json.writerWithDefaultPrettyPrinter()
                .writeValue(output.resolve("requiem-restore-request.json").toFile(), recovery);
        System.out.println(
                "Snapshot saved: " + ids.size() + " achievements; testing " + chosen + " (originally locked).");
        boolean attempted = false, restoreConfirmed = false;
        Throwable testFailure = null;
        try {
            attempted = true;
            var saved = steam.execute(new Request(1182900, account, ids, Map.of(chosen, false), Map.of(chosen, true)));
            json.writerWithDefaultPrettyPrinter()
                    .writeValue(output.resolve("requiem-unlock-result.json").toFile(), saved);
            if (saved.status() != Status.CONFIRMED)
                throw new IllegalStateException("Unlock was not confirmed: " + saved.status() + ": " + saved.message());
            var unlocked = read(steam, account, ids);
            requireKnown(unlocked);
            for (String id : ids) {
                var expected = id.equals(chosen) ? AchievementState.UNLOCKED : baseline.achievements().get(id).state();
                if (unlocked.achievements().get(id).state() != expected)
                    throw new IllegalStateException("Unexpected state after unlock: " + id);
            }
            System.out.println(
                    "Unlock acknowledged by Steam and verified in a fresh native session; other states unchanged.");
        } catch (Throwable ex) {
            testFailure = ex;
        } finally {
            if (attempted) {
                // Always restore even if the unlock call failed or its outcome was uncertain.
                for (int attempt = 0; attempt < 3 && !restoreConfirmed; attempt++) {
                    try {
                        if (attempt > 0)
                            Thread.sleep(10000L * attempt);
                        var current = read(steam, account, ids);
                        requireKnown(current);
                        boolean unlocked = current.achievements().get(chosen).state() == AchievementState.UNLOCKED;
                        var restored = steam.execute(
                                new Request(1182900, account, ids, Map.of(chosen, unlocked), Map.of(chosen, false)));
                        json.writerWithDefaultPrettyPrinter()
                                .writeValue(output.resolve("requiem-restore-result.json").toFile(), restored);
                        restoreConfirmed = restored.status() == Status.CONFIRMED;
                    } catch (Exception ex) {
                        System.out.println(
                                "Restore attempt did not confirm; retrying within the bounded recovery check.");
                    }
                }
                var after = read(steam, account, ids);
                json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("requiem-after.json").toFile(), after);
                requireKnown(after);
                for (String id : ids) {
                    var before = baseline.achievements().get(id);
                    var now = after.achievements().get(id);
                    if (before.state() != now.state())
                        throw new IllegalStateException("RESTORE INCOMPLETE: " + id + "; use the saved baseline.");
                    if (before.state() == AchievementState.UNLOCKED && before.unlockTime() != now.unlockTime())
                        throw new IllegalStateException("An existing unlock timestamp changed: " + id);
                }
                if (!restoreConfirmed)
                    throw new IllegalStateException(
                            "States match locally, but Steam did not acknowledge restoration. Recovery request is " +
                                    "saved.");
                System.out.println("Restore acknowledged by Steam. All " + ids.size() +
                        " achievement states match baseline; existing unlock timestamps unchanged.");
            }
        }
        if (testFailure != null)
            throw new IllegalStateException("Unlock test failed; restoration completed.", testFailure);
    }

    private static Response read(NativeSteam steam, String account, List<String> ids) throws Exception {
        var response = steam.execute(new Request(1182900, account, ids, Map.of(), Map.of()));
        if (response.status() != Status.READ)
            throw new IllegalStateException(response.message());
        return response;
    }

    private static void requireKnown(Response response) {
        if (response.achievements().isEmpty() ||
                response.achievements().values().stream().anyMatch(a -> a.state() == AchievementState.UNKNOWN))
            throw new IllegalStateException("Full native state is unavailable; cannot verify the baseline.");
    }
}
