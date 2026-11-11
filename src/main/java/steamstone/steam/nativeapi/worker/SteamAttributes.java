package steamstone.steam.nativeapi.worker;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import steamstone.exception.StoneException;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.nativeapi.protocol.Entry;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Two documented flat C API reads missing from steamworks4j 1.10.0. SDK/runtime version is pinned.
 */
public final class SteamAttributes implements AutoCloseable {
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment stats;
    private final MethodHandle attribute, read;

    public SteamAttributes(final Path library) {
        try {
            final var symbols = SymbolLookup.libraryLookup(library, arena);
            final var linker = Linker.nativeLinker();
            final var getter = linker.downcallHandle(symbols.find("SteamAPI_SteamUserStats_v013").orElseThrow(),
                    FunctionDescriptor.of(ADDRESS));
            stats = (MemorySegment) getter.invokeExact();
            if (stats.equals(MemorySegment.NULL))
                throw new StoneException("Steam stats interface unavailable.");
            attribute = linker.downcallHandle(
                    symbols.find("SteamAPI_ISteamUserStats_GetAchievementDisplayAttribute").orElseThrow(),
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            read = linker.downcallHandle(symbols.find("SteamAPI_ISteamUserStats_GetAchievementAndUnlockTime").orElseThrow(),
                    FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        } catch (final Throwable exception) {
            try (arena) {
                throw new StoneException("Could not initialize Steam achievement reads.", exception);
            }
        }
    }

    public Entry read(final String id) {
        try (final var temporary = Arena.ofConfined()) {
            final var name = temporary.allocateFrom(id);
            final var achieved = temporary.allocate(JAVA_BOOLEAN);
            final var time = temporary.allocate(JAVA_INT);
            final var valid = (boolean) read.invokeExact(stats, name, achieved, time);
            final var pointer = (MemorySegment) attribute.invokeExact(stats, name, temporary.allocateFrom("desc"));
            final var description = pointer.equals(MemorySegment.NULL) ? "" : pointer.reinterpret(65536).getString(0);
            return new Entry(!valid ? AchievementState.UNKNOWN
                    : achieved.get(JAVA_BOOLEAN, 0) ? AchievementState.UNLOCKED : AchievementState.LOCKED,
                    description, valid ? Integer.toUnsignedLong(time.get(JAVA_INT, 0)) : 0);
        } catch (final Throwable ex) {
            throw new StoneException("Steam achievement read failed.", ex);
        }
    }

    @Override
    public void close() {
        arena.close();
    }
}
