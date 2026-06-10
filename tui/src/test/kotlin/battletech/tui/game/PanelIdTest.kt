package battletech.tui.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PanelIdTest {

    /**
     * The index doubles as the user-facing `Alt+N` collapse digit and the
     * `drawBox` decoration. Pin the values so a future reorder of the enum
     * can't silently remap which panel each keystroke collapses.
     */
    @Test
    fun `panel indices are stable`() {
        assertEquals(0, PanelId.LOG.index)
        assertEquals(1, PanelId.UNIT_STATUS.index)
        assertEquals(2, PanelId.DECLARED_TARGETS.index)
        assertEquals(3, PanelId.TARGETS.index)
        assertEquals(4, PanelId.TARGET_STATUS.index)
        assertEquals(5, PanelId.ATTACK_RESULTS.index)
    }
}
