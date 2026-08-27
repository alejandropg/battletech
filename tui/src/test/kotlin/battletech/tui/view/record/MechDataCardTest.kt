package battletech.tui.view.record

import battletech.tui.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.view.line
import tenter.view.render
import tenter.view.text

/** [MechDataCard] — the identity/tonnage/movement card shared by own and enemy record sheets. */
internal class MechDataCardTest {

    @Test
    fun `prints tonnage and movement points`() {
        val unit = aUnit(walkingMP = 3, runningMP = 5, jumpMP = 0)
        val buffer = render(MechDataCard(unit), width = 36, height = 10)
        val text = buffer.text()

        assertTrue(text.contains("Tonnage : 50"))
        assertTrue(text.contains("Walking : 3"))
        assertTrue(text.contains("Running : 5"))
        assertFalse(text.contains("Jumping"))
    }

    @Test
    fun `prints jump points only when the unit can jump`() {
        val unit = aUnit(jumpMP = 4)
        val buffer = render(MechDataCard(unit), width = 36, height = 10)

        assertTrue(buffer.text().contains("Jumping : 4"))
    }

    @Test
    fun `prints each special status on a separate line`() {
        val unit = aUnit().copy(
            isDestroyed = true,
            isShutdown = true,
            isPilotConscious = false,
            isProne = true,
        )
        val buffer = render(MechDataCard(unit), width = 36, height = 12)

        assertEquals("DESTROYED", buffer.line(6, width = 36).trim())
        assertEquals("SHUTDOWN", buffer.line(7, width = 36).trim())
        assertEquals("PILOT UNCONSCIOUS", buffer.line(8, width = 36).trim())
        assertEquals("PRONE", buffer.line(9, width = 36).trim())
    }
}
