package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.view.line
import io.archinaut.tenter.view.render
import io.archinaut.tenter.view.text

internal class SpecialUnitStatusListTest {

    @Test
    fun `renders nothing when the unit has no special status`() {
        val buffer = render(SpecialUnitStatusList(aUnit()), width = 24, height = 8)

        assertTrue(buffer.text().isBlank())
    }

    @Test
    fun `renders every special status on a separate danger-colored line`() {
        val unit = aUnit().copy(
            isDestroyed = true,
            isShutdown = true,
            isPilotConscious = false,
            isProne = true,
        )
        val buffer = render(SpecialUnitStatusList(unit), width = 24, height = 8)

        assertTrue(buffer.line(0, width = 24).isBlank())
        assertEquals("DESTROYED", buffer.line(1, width = 24).trim())
        assertEquals("SHUTDOWN", buffer.line(2, width = 24).trim())
        assertEquals("PILOT UNCONSCIOUS", buffer.line(3, width = 24).trim())
        assertEquals("PRONE", buffer.line(4, width = 24).trim())
        for (row in 1..4) {
            assertEquals(ChromeRole.DANGER, buffer.get(0, row).style.fg)
        }
    }
}
