package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.aUnit
import io.archinaut.tenter.color.StandardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Rect

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

        assertTrue(buffer.region(Rect(0, 0, 24, 1)).line(0).isBlank())
        assertEquals("DESTROYED", buffer.region(Rect(0, 1, 24, 1)).line(0).trim())
        assertEquals("SHUTDOWN", buffer.region(Rect(0, 2, 24, 1)).line(0).trim())
        assertEquals("PILOT UNCONSCIOUS", buffer.region(Rect(0, 3, 24, 1)).line(0).trim())
        assertEquals("PRONE", buffer.region(Rect(0, 4, 24, 1)).line(0).trim())
        for (row in 1..4) {
            assertEquals(StandardRole.DANGER, buffer.get(0, row).style.fg)
        }
    }
}
