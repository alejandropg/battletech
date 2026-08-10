package battletech.tui.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PanelIdTest {

    /**
     * The key doubles as the user-facing `Alt+<key>` chord and the `drawBox`
     * decoration badge. Pin the values so a future reorder of the enum
     * can't silently remap which panel each keystroke acts on.
     */
    @Test
    fun `panel keys are stable`() {
        assertEquals('0', PanelId.LOG.key)
        assertEquals('1', PanelId.UNIT_STATUS.key)
        assertEquals('2', PanelId.DECLARED_TARGETS.key)
        assertEquals('3', PanelId.TARGETS.key)
        assertEquals('4', PanelId.TARGET_STATUS.key)
        assertEquals('5', PanelId.ATTACK_RESULTS.key)
        assertEquals('h', PanelId.HELP.key)
    }

    @Test
    fun `byKey resolves every panel and returns null for an unknown key`() {
        for (panel in PanelId.entries) {
            assertEquals(panel, PanelId.byKey(panel.key))
        }
        assertEquals(null, PanelId.byKey('z'))
    }
}
