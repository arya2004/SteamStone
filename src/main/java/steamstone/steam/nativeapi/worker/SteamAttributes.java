package steamstone.steam.nativeapi.worker;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import steamstone.exception.StoneException;
import steamstone.exception.ResourceCleanup;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.protocol.Entry;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Two documented flat C API reads missing from steamworks4j 1.10.0. SDK/runtime version is pinned.
 */
public final class SteamAttributes implements AutoCloseable {
    private static final String USER_STATS_SYMBOL = "SteamAPI_SteamUserStats_v013";
    private static final String ACHIEVEMENT_ATTRIBUTE_SYMBOL =
            "SteamAPI_ISteamUserStats_GetAchievementDisplayAttribute";
    private static final String ACHIEVEMENT_STATE_SYMBOL = "SteamAPI_ISteamUserStats_GetAchievementAndUnlockTime";

    private static final String DESCRIPTION_ATTRIBUTE = "desc";
    private static final String EMPTY_DESCRIPTION = "";
    private static final long MAX_DESCRIPTION_BYTES = 65_536;
    private static final long VALUE_OFFSET = 0;
    private static final long UNKNOWN_UNLOCK_TIME = 0;

    private final Arena arena = Arena.ofConfined();
    private final MemorySegment stats;
    private final MethodHandle attribute, read;

    public SteamAttributes(final Path library) {
        try {
            final var symbols = SymbolLookup.libraryLookup(library, arena);
            final var linker = Linker.nativeLinker();
            final var getter = linker.downcallHandle(symbols.find(USER_STATS_SYMBOL).orElseThrow(),
                    FunctionDescriptor.of(ADDRESS));
            stats = (MemorySegment) getter.invokeExact();
            if (stats.equals(MemorySegment.NULL))
                throw new StoneException("Steam stats interface unavailable.");
            attribute = linker.downcallHandle(
                    symbols.find(ACHIEVEMENT_ATTRIBUTE_SYMBOL).orElseThrow(),
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            read = linker.downcallHandle(
                    symbols.find(ACHIEVEMENT_STATE_SYMBOL).orElseThrow(),
                    FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        } catch (final Throwable e) {
            throw ResourceCleanup.afterFailure("Could not initialize Steam achievement reads.", e, arena::close);
        }
    }

    public Entry read(final String id) {
        try (final var temporary = Arena.ofConfined()) {
            final var name = temporary.allocateFrom(id);
            final var achieved = temporary.allocate(JAVA_BOOLEAN);
            final var time = temporary.allocate(JAVA_INT);
            final var valid = (boolean) read.invokeExact(stats, name, achieved, time);
            final var pointer =
                    (MemorySegment) attribute.invokeExact(stats, name, temporary.allocateFrom(DESCRIPTION_ATTRIBUTE));
            final var description = pointer.equals(MemorySegment.NULL) ? EMPTY_DESCRIPTION :
                    pointer.reinterpret(MAX_DESCRIPTION_BYTES).getString(VALUE_OFFSET);
            return new Entry(!valid ? AchievementState.UNKNOWN
                    : achieved.get(JAVA_BOOLEAN, VALUE_OFFSET) ? AchievementState.UNLOCKED : AchievementState.LOCKED,
                    description,
                    valid ? Integer.toUnsignedLong(time.get(JAVA_INT, VALUE_OFFSET)) : UNKNOWN_UNLOCK_TIME);
        } catch (final Throwable e) {
            throw StoneException.wrap("Steam achievement read failed.", e);
        }
    }

    @Override
    public void close() {
        arena.close();
    }
}
