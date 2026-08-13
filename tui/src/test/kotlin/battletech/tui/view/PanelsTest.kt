package battletech.tui.view

import battletech.tui.game.GamePanelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.panel.Panel

internal class PanelsTest {

    @Test
    fun `registry is in left-to-right render order, HELP rightmost`() {
        assertEquals(
            listOf(
                GamePanelId.TARGET_STATUS,
                GamePanelId.TARGETS,
                GamePanelId.DECLARED_TARGETS,
                GamePanelId.ATTACK_RESULTS,
                GamePanelId.UNIT_STATUS,
                GamePanelId.LOG,
                GamePanelId.HELP,
            ),
            Panels.build().map { it.id },
        )
    }

    @Test
    fun `all panels are the same width`() {
        val byId = Panels.build().associateBy { it.id }
        assertEquals(28, byId.getValue(GamePanelId.ATTACK_RESULTS).width)
        assertEquals(28, byId.getValue(GamePanelId.LOG).width)
        assertEquals(28, byId.getValue(GamePanelId.TARGETS).width)
        assertEquals(28, byId.getValue(GamePanelId.HELP).width)
    }

    @Test
    fun `every panel id appears exactly once`() {
        assertEquals(GamePanelId.entries.toSet(), Panels.build().map { it.id }.toSet())
        assertEquals(GamePanelId.entries.size, Panels.build().size)
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
        val first = Panels.build().first { it.id == GamePanelId.LOG }
        first.toggleCollapsed()

        val second = Panels.build().first { it.id == GamePanelId.LOG }

        assertEquals(false, second.collapsed, "a later Panels.build() must not see an earlier call's state")
    }
}
