package steamstone.steam.nativeapi;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import steamstone.model.Achievement;

import static java.lang.foreign.ValueLayout.*;

/**
 * Two documented flat C API reads missing from steamworks4j 1.10.0. SDK/runtime version is pinned.
 */
final class SteamAttributes implements AutoCloseable {
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment stats;
    private final MethodHandle attribute, read;

    SteamAttributes(Path library) throws Throwable {
        var symbols = SymbolLookup.libraryLookup(library, arena);
        var linker = Linker.nativeLinker();
        var getter = linker.downcallHandle(symbols.find("SteamAPI_SteamUserStats_v013").orElseThrow(),
                FunctionDescriptor.of(ADDRESS));
        stats = (MemorySegment) getter.invokeExact();
        if (stats.equals(MemorySegment.NULL))
            throw new IllegalStateException("Steam stats interface unavailable.");
        attribute = linker.downcallHandle(
                symbols.find("SteamAPI_ISteamUserStats_GetAchievementDisplayAttribute").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        read = linker.downcallHandle(symbols.find("SteamAPI_ISteamUserStats_GetAchievementAndUnlockTime").orElseThrow(),
                FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    }

    NativeProtocol.Entry read(String id) {
        try (var temporary = Arena.ofConfined()) {
            var name = temporary.allocateFrom(id);
            var achieved = temporary.allocate(JAVA_BOOLEAN);
            var time = temporary.allocate(JAVA_INT);
            boolean valid = (boolean) read.invokeExact(stats, name, achieved, time);
            var pointer = (MemorySegment) attribute.invokeExact(stats, name, temporary.allocateFrom("desc"));
            String description = pointer.equals(MemorySegment.NULL) ? "" : pointer.reinterpret(65536).getString(0);
            return new NativeProtocol.Entry(!valid ? Achievement.State.UNKNOWN
                    : achieved.get(JAVA_BOOLEAN, 0) ? Achievement.State.UNLOCKED : Achievement.State.LOCKED,
                    description, valid ? Integer.toUnsignedLong(time.get(JAVA_INT, 0)) : 0);
        } catch (Throwable ex) {
            throw new IllegalStateException("Steam achievement read failed.", ex);
        }
    }

    @Override
    public void close() {
        arena.close();
    }
}
