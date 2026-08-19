package battletech.tui.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GamePanelIdTest {

    /**
     * The badge doubles as the user-facing `Alt+<badge>` chord and the bordered
     * decoration badge. Pin the values so a future reorder of the enum
     * can't silently remap which panel each keystroke acts on.
     */
    @Test
    fun `panel badges are stable`() {
        assertEquals('0', GamePanelId.BOARD.badge)
        assertEquals('1', GamePanelId.UNIT_STATUS.badge)
        assertEquals('2', GamePanelId.DECLARED_TARGETS.badge)
        assertEquals('3', GamePanelId.TARGETS.badge)
        assertEquals('4', GamePanelId.TARGET_STATUS.badge)
        assertEquals('5', GamePanelId.ATTACK_RESULTS.badge)
        assertEquals('9', GamePanelId.LOG.badge)
        assertEquals('h', GamePanelId.HELP.badge)
    }

    @Test
    fun `every badge is unique`() {
        val badges = GamePanelId.entries.map { it.badge }
        assertEquals(badges.size, badges.toSet().size, "duplicate badge would let one chord ambiguously resolve two panels")
    }

    @Test
    fun `byBadge resolves every panel and returns null for an unknown badge`() {
        for (panel in GamePanelId.entries) {
            assertEquals(panel, GamePanelId.byBadge(panel.badge))
        }
        assertEquals(null, GamePanelId.byBadge('z'))
    }
}
