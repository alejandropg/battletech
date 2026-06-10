package battletech.tui.view

import battletech.tui.game.PanelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PanelsTest {

    @Test
    fun `registry is in left-to-right render order`() {
        assertEquals(
            listOf(
                PanelId.TargetStatus,
                PanelId.Targets,
                PanelId.DeclaredTargets,
                PanelId.AttackResults,
                PanelId.UnitStatus,
                PanelId.Log,
            ),
            Panels.ordered.map { it.id },
        )
    }

    @Test
    fun `attack results panel is wider than the rest`() {
        val byId = Panels.ordered.associateBy { it.id }
        assertEquals(36, byId.getValue(PanelId.AttackResults).width)
        assertEquals(28, byId.getValue(PanelId.Log).width)
        assertEquals(28, byId.getValue(PanelId.Targets).width)
    }

    @Test
    fun `every panel id appears exactly once`() {
        assertEquals(PanelId.entries.toSet(), Panels.ordered.map { it.id }.toSet())
        assertEquals(PanelId.entries.size, Panels.ordered.size)
    }
}
