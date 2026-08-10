package battletech.tui.view

import battletech.tui.game.PanelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PanelsTest {

    @Test
    fun `registry is in left-to-right render order, HELP rightmost`() {
        assertEquals(
            listOf(
                PanelId.TARGET_STATUS,
                PanelId.TARGETS,
                PanelId.DECLARED_TARGETS,
                PanelId.ATTACK_RESULTS,
                PanelId.UNIT_STATUS,
                PanelId.LOG,
                PanelId.HELP,
            ),
            Panels.ordered.map { it.id },
        )
    }

    @Test
    fun `all panels are the same width`() {
        val byId = Panels.ordered.associateBy { it.id }
        assertEquals(28, byId.getValue(PanelId.ATTACK_RESULTS).width)
        assertEquals(28, byId.getValue(PanelId.LOG).width)
        assertEquals(28, byId.getValue(PanelId.TARGETS).width)
        assertEquals(28, byId.getValue(PanelId.HELP).width)
    }

    @Test
    fun `every panel id appears exactly once`() {
        assertEquals(PanelId.entries.toSet(), Panels.ordered.map { it.id }.toSet())
        assertEquals(PanelId.entries.size, Panels.ordered.size)
    }

    @Test
    fun `HELP is the only panel that vanishes entirely when collapsed`() {
        val byId = Panels.ordered.associateBy { it.id }
        assertEquals(0, byId.getValue(PanelId.HELP).collapsedWidth)
        for (id in PanelId.entries - PanelId.HELP) {
            assertTrue(byId.getValue(id).collapsedWidth > 0) { "$id should not vanish when collapsed" }
        }
    }
}
