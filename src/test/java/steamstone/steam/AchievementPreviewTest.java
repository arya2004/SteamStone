package steamstone.steam;

import org.junit.jupiter.api.Test;
import steamstone.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AchievementPreviewTest {
    private final Achievement locked = new Achievement("A", "Alpha", "", Achievement.State.LOCKED, 0, "", "");
    private final Achievement unlocked = new Achievement("B", "Beta", "", Achievement.State.UNLOCKED, 123, "", "");
    private final Achievement unknown = new Achievement("C", "Gamma", "", Achievement.State.UNKNOWN, 0, "", "");

    @Test
    void revertingCheckboxRemovesPendingChangeWithoutChangingSteamSnapshot() {
        var preview = new AchievementPreview(List.of(locked, unlocked));
        preview.setUnlocked(locked, true);
        assertEquals(1, preview.changeCount());
        assertTrue(preview.isUnlocked(locked));
        assertEquals(Achievement.State.LOCKED, locked.state());
        preview.setUnlocked(locked, false);
        assertEquals(0, preview.changeCount());
    }

    @Test
    void bulkPreviewSkipsUnknownAndResetRestoresOriginal() {
        var preview = new AchievementPreview(List.of(locked, unlocked, unknown));
        preview.setAll(true);
        assertEquals(1, preview.changeCount());
        assertFalse(preview.isChanged(unknown));
        preview.setAll(false);
        assertEquals(1, preview.changeCount());
        assertTrue(preview.isChanged(unlocked));
        preview.reset();
        assertEquals(0, preview.changeCount());
        assertTrue(preview.isUnlocked(unlocked));
        assertFalse(preview.isUnlocked(locked));
    }

    @Test
    void foreignAndUnknownRowsCannotBeEdited() {
        var preview = new AchievementPreview(List.of(unknown));
        preview.setUnlocked(locked, true);
        preview.setUnlocked(unknown, true);
        assertEquals(0, preview.changeCount());
    }
}
