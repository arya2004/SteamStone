package steamstone.model.achievement;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementPreviewTest {
    private final Achievement locked = new Achievement("A", "Alpha", "", AchievementState.LOCKED, 0, "", "");
    private final Achievement unlocked = new Achievement("B", "Beta", "", AchievementState.UNLOCKED, 123, "", "");
    private final Achievement unknown = new Achievement("C", "Gamma", "", AchievementState.UNKNOWN, 0, "", "");

    @Test
    void revertingCheckboxRemovesPendingChangeWithoutChangingSteamSnapshot() {
        var preview = new AchievementPreview(List.of(locked, unlocked));
        preview.setUnlocked(locked, true);
        assertEquals(1, preview.changeCount());
        assertTrue(preview.isUnlocked(locked));
        assertEquals(AchievementState.LOCKED, locked.state());
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
