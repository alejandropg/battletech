package battletech.tui.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PanelIdTest {

    /**
     * The badge doubles as the user-facing `Alt+<badge>` chord and the bordered
     * decoration badge. Pin the values so a future reorder of the enum
     * can't silently remap which panel each keystroke acts on.
     */
    @Test
    fun `panel badges are stable`() {
        assertEquals('0', PanelId.LOG.badge)
        assertEquals('1', PanelId.UNIT_STATUS.badge)
        assertEquals('2', PanelId.DECLARED_TARGETS.badge)
        assertEquals('3', PanelId.TARGETS.badge)
        assertEquals('4', PanelId.TARGET_STATUS.badge)
        assertEquals('5', PanelId.ATTACK_RESULTS.badge)
        assertEquals('h', PanelId.HELP.badge)
    }

    @Test
    fun `byBadge resolves every panel and returns null for an unknown badge`() {
        for (panel in PanelId.entries) {
            assertEquals(panel, PanelId.byBadge(panel.badge))
        }
        assertEquals(null, PanelId.byBadge('z'))
    }
}
