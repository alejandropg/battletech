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
        assertEquals(0, PanelId.Log.index)
        assertEquals(1, PanelId.UnitStatus.index)
        assertEquals(2, PanelId.DeclaredTargets.index)
        assertEquals(3, PanelId.Targets.index)
        assertEquals(4, PanelId.TargetStatus.index)
        assertEquals(5, PanelId.AttackResults.index)
    }
}
