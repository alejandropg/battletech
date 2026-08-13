package battletech.tui.view

import battletech.tui.game.PanelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.panel.Panel

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
            Panels.build().map { it.id },
        )
    }

    @Test
    fun `all panels are the same width`() {
        val byId = Panels.build().associateBy { it.id }
        assertEquals(28, byId.getValue(PanelId.ATTACK_RESULTS).width)
        assertEquals(28, byId.getValue(PanelId.LOG).width)
        assertEquals(28, byId.getValue(PanelId.TARGETS).width)
        assertEquals(28, byId.getValue(PanelId.HELP).width)
    }

    @Test
    fun `every panel id appears exactly once`() {
        assertEquals(PanelId.entries.toSet(), Panels.build().map { it.id }.toSet())
        assertEquals(PanelId.entries.size, Panels.build().size)
    }

    @Test
    fun `every panel — HELP included — collapses to the same uniform stub width`() {
        // Unlike before, HELP no longer special-cases its own collapse to vanish entirely: its
        // existence is governed by AppState.helpOpen (see PanelVisibility), not by collapsing to
        // width 0. Once visible, HELP collapses just like any other panel.
        for (panel in Panels.build()) {
            panel.toggleCollapsed()
            assertEquals(Panel.COLLAPSED_WIDTH, panel.width) { "${panel.id} should collapse to the uniform stub width" }
        }
    }

    @Test
    fun `build returns a fresh, independent instance every call`() {
        val first = Panels.build().first { it.id == PanelId.LOG }
        first.toggleCollapsed()

        val second = Panels.build().first { it.id == PanelId.LOG }

        assertEquals(false, second.collapsed, "a later Panels.build() must not see an earlier call's state")
    }
}
