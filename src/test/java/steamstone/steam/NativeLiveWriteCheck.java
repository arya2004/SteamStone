package steamstone.steam;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import steamstone.concurrent.StoneFutures;
import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;
import steamstone.model.achievement.Achievement;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.NativeSteam;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Response;
import steamstone.steam.nativeapi.protocol.Status;

/**
 * Explicit opt-in only: unlock one achievement and restore the complete baseline.
 */
public final class NativeLiveWriteCheck {

    public static void main(final String[] args) {
        if (args.length != 1 || !args[0].equals("--unlock-and-restore-one-locked-requiem-achievement")) {
            throw new StoneException(ErrorCode.APPLICATION);
        }
        try {
            final var account = System.getenv("STEAMSTONE_TEST_PROFILE");
            final var keyFile = System.getenv("STEAMSTONE_TEST_KEY_FILE");
            final var matcher = Pattern.compile("(?<![a-fA-F0-9])[a-fA-F0-9]{32}(?![a-fA-F0-9])")
                    .matcher(Files.readString(Path.of(keyFile)));
            if (!matcher.find()) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            final var web = new SteamWebApi().loadAchievements(matcher.group(), account, 1182900);
            final var ids = web.achievements().stream().map(Achievement::id).toList();
            if (ids.isEmpty()) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            final var steam = new NativeSteam();
            final var json = new ObjectMapper();
            final var output = Path.of("target", "verification");
            Files.createDirectories(output);
            final var baseline = read(steam, account, ids);
            requireKnown(baseline);
            save(json, output, "requiem-before.json", baseline);
            final var chosen = ids.stream()
                    .filter((final var id) -> baseline.achievements().get(id).state() == AchievementState.LOCKED)
                    .findFirst().orElseThrow(() -> new StoneException(ErrorCode.APPLICATION));
            save(json, output, "requiem-restore-request.json",
                    new Request(1182900, account, ids, Map.of(chosen, true), Map.of(chosen, false)));
            System.out.println("Snapshot saved: " + ids.size() + " achievements; testing " + chosen
                    + " (originally locked).");
            try {
                unlockAndVerify(steam, account, ids, chosen, baseline, json, output);
            } finally {
                // Always restore, including after an uncertain or failed unlock call.
                final var confirmed = restore(steam, account, ids, chosen, json, output, 0);
                final var after = read(steam, account, ids);
                save(json, output, "requiem-after.json", after);
                verifyBaseline(baseline, after);
                if (!confirmed) {
                    throw new StoneException(ErrorCode.APPLICATION);
                }
                System.out.println("Restore acknowledged by Steam. All " + ids.size()
                        + " achievement states and existing unlock timestamps match baseline.");
            }
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    private static void unlockAndVerify(final NativeSteam steam, final String account, final List<String> ids,
                                        final String chosen, final Response baseline, final ObjectMapper json,
                                        final Path output) {
        final var saved =
                steam.execute(new Request(1182900, account, ids, Map.of(chosen, false), Map.of(chosen, true)));
        save(json, output, "requiem-unlock-result.json", saved);
        if (saved.status() != Status.CONFIRMED) {
            throw new StoneException(ErrorCode.APPLICATION);
        }
        final var unlocked = read(steam, account, ids);
        requireKnown(unlocked);
        for (final var id : ids) {
            final var expected =
                    id.equals(chosen) ? AchievementState.UNLOCKED : baseline.achievements().get(id).state();
            if (unlocked.achievements().get(id).state() != expected) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
        }
        System.out.println("Unlock acknowledged by Steam and verified in a fresh native session.");
    }

    private static boolean restore(final NativeSteam steam, final String account, final List<String> ids,
                                   final String chosen, final ObjectMapper json, final Path output, final int attempt) {
        final var confirmed =
                StoneFutures.await(CompletableFuture.completedFuture(attempt).thenApply((final var index) -> {
                    if (index > 0) {
                        pause(10000L * index);
                    }
                    final var current = read(steam, account, ids);
                    requireKnown(current);
                    final var unlocked = current.achievements().get(chosen).state() == AchievementState.UNLOCKED;
                    final var restored = steam.execute(new Request(1182900, account, ids,
                            Map.of(chosen, unlocked), Map.of(chosen, false)));
                    save(json, output, "requiem-restore-result.json", restored);
                    return restored.status() == Status.CONFIRMED;
                }).handle((final var result, final var e) -> {
                    if (e != null) {
                        System.out.println(
                                "Restore attempt did not confirm; recovery request and baseline remain saved.");
                    }
                    return e == null && result;
                }));
        return confirmed || (attempt < 2 && !Thread.currentThread().isInterrupted()
                && restore(steam, account, ids, chosen, json, output, attempt + 1));
    }

    private static void pause(final long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (final InterruptedException e) {
            throw new StoneException(ErrorCode.CANCELLED,
                    e);
        }
    }

    private static void save(final ObjectMapper json, final Path output, final String name, final Object value) {
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve(name).toFile(), value);
        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    private static Response read(final NativeSteam steam, final String account, final List<String> ids) {
        final var response = steam.execute(new Request(1182900, account, ids, Map.of(), Map.of()));
        if (response.status() != Status.READ) {
            throw new StoneException(response.errorCode());
        }
        return response;
    }

    private static void verifyBaseline(final Response before, final Response after) {
        requireKnown(after);
        for (final var id : before.achievements().keySet()) {
            final var original = before.achievements().get(id);
            final var restored = after.achievements().get(id);
            if (restored == null || original.state() != restored.state()) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            if (original.state() == AchievementState.UNLOCKED && original.unlockTime() != restored.unlockTime()) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
        }
    }

    private static void requireKnown(final Response response) {
        if (response.achievements().isEmpty() || response.achievements().values().stream()
                .anyMatch((final var achievement) -> achievement.state() == AchievementState.UNKNOWN)) {
            throw new StoneException(ErrorCode.APPLICATION);
        }
    }
}
