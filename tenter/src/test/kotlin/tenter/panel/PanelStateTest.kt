package tenter.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.view.contentView

private enum class TestPanelId : PanelId { A, B }

internal class PanelStateTest {

    private fun panel(
        minimized: Boolean = true,
        maximized: Boolean = true,
    ): Panel<TestPanelId, Unit> = Panel(
        id = TestPanelId.A,
        title = "T",
        normal = { Panel.Presentation.fixedWidth(contentView { }, 20) },
        minimized = if (minimized) ({ Panel.Presentation.fixedWidth(contentView { }, Panel.MINIMIZED_WIDTH) }) else null,
        maximized = if (maximized) ({ Panel.Presentation.allocated(contentView { }) }) else null,
    )

    @Test
    fun `states list exactly the declared states in cycle order`() {
        assertEquals(
            listOf(PanelState.MINIMIZED, PanelState.NORMAL, PanelState.MAXIMIZED),
            panel().states,
        )
        assertEquals(listOf(PanelState.NORMAL), panel(false, false).states)
        assertEquals(listOf(PanelState.MINIMIZED, PanelState.NORMAL), panel(true, false).states)
    }

    @Test
    fun `set operations cycle forward and backward and skip undeclared states`() {
        val set = PanelSet.uniform(listOf(panel()))

        set.cycleFocusedState(1)
        assertEquals(PanelState.MAXIMIZED, set.stateOf(TestPanelId.A))
        set.cycleFocusedState(1)
        assertEquals(PanelState.MINIMIZED, set.stateOf(TestPanelId.A))
        set.cycleFocusedState(-1)
        assertEquals(PanelState.MAXIMIZED, set.stateOf(TestPanelId.A))

        val minimizedAndNormal = PanelSet.uniform(listOf(panel(minimized = true, maximized = false)))
        minimizedAndNormal.cycleFocusedState(1)
        assertEquals(PanelState.MINIMIZED, minimizedAndNormal.stateOf(TestPanelId.A))
    }

    @Test
    fun `maximized state restores to the state from which it was entered`() {
        val a = panel()
        val b = Panel<TestPanelId, Unit>(
            id = TestPanelId.B,
            title = "B",
            normal = { Panel.Presentation.fixedWidth(contentView { }, 20) },
        )
        val set = PanelSet.uniform(listOf(a, b))
        set.cycleFocusedState(-1)
        set.cycleFocusedState(-1)
        assertEquals(PanelState.MAXIMIZED, set.stateOf(TestPanelId.A))
        set.focus(TestPanelId.B)
        assertEquals(PanelState.MINIMIZED, set.stateOf(TestPanelId.A))
    }

    @Test
    fun `fixed presentation rejects nonpositive widths`() {
        for (width in listOf(-1, 0)) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
                Panel.Presentation.fixedWidth(contentView { }, width)
            }
        }
    }
}
