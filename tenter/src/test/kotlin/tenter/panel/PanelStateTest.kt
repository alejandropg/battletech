package tenter.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.view.View

private enum class TestPanelId : PanelId { A }

/** [Panel]'s [PanelState] cycling, restore-on-demote, and per-state width — see [Panel.cycleState]'s KDoc. */
internal class PanelStateTest {

    private fun stubView(): View = object : View {
        override fun draw(canvas: Canvas) = Unit
    }

    private fun fullPanel(): Panel<TestPanelId, Unit> = Panel(
        id = TestPanelId.A,
        title = "T",
        normalWidth = 20,
        normal = { stubView() },
        minimized = { stubView() },
        maximized = { stubView() },
    )

    private fun normalOnlyPanel(): Panel<TestPanelId, Unit> = Panel(
        id = TestPanelId.A,
        title = "T",
        normalWidth = 20,
        normal = { stubView() },
    )

    @Test
    fun `states lists exactly the declared states, smallest first`() {
        assertEquals(listOf(PanelState.MINIMIZED, PanelState.NORMAL, PanelState.MAXIMIZED), fullPanel().states)
        assertEquals(listOf(PanelState.NORMAL), normalOnlyPanel().states)
    }

    @Test
    fun `cycleState steps forward through declared states and wraps`() {
        val panel = fullPanel()
        assertEquals(PanelState.NORMAL, panel.state)

        panel.cycleState(1)
        assertEquals(PanelState.MAXIMIZED, panel.state)

        panel.cycleState(1)
        assertEquals(PanelState.MINIMIZED, panel.state, "wraps past MAXIMIZED back to MINIMIZED")
    }

    @Test
    fun `cycleState steps backward through declared states and wraps`() {
        val panel = fullPanel()

        panel.cycleState(-1)
        assertEquals(PanelState.MINIMIZED, panel.state)

        panel.cycleState(-1)
        assertEquals(PanelState.MAXIMIZED, panel.state, "wraps past MINIMIZED back to MAXIMIZED")
    }

    @Test
    fun `cycling skips undeclared states`() {
        val minimizedAndNormal = Panel<TestPanelId, Unit>(
            id = TestPanelId.A,
            title = "T",
            normalWidth = 20,
            normal = { stubView() },
            minimized = { stubView() },
        )
        assertEquals(listOf(PanelState.MINIMIZED, PanelState.NORMAL), minimizedAndNormal.states)

        minimizedAndNormal.cycleState(1) // NORMAL -> wraps straight to MINIMIZED, no MAXIMIZED declared
        assertEquals(PanelState.MINIMIZED, minimizedAndNormal.state)
    }

    @Test
    fun `a NORMAL-only panel is inert to cycling`() {
        val panel = normalOnlyPanel()

        panel.cycleState(1)
        assertEquals(PanelState.NORMAL, panel.state)

        panel.cycleState(-1)
        assertEquals(PanelState.NORMAL, panel.state)
    }

    @Test
    fun `entering MAXIMIZED from MINIMIZED then demoting returns to MINIMIZED, not NORMAL`() {
        val panel = fullPanel()
        panel.cycleState(-1) // NORMAL -> MINIMIZED
        assertEquals(PanelState.MINIMIZED, panel.state)

        panel.cycleState(-1) // MINIMIZED -> MAXIMIZED (wrap), recording MINIMIZED as the restore state
        assertEquals(PanelState.MAXIMIZED, panel.state)

        panel.demoteFromMaximized()
        assertEquals(PanelState.MINIMIZED, panel.state)
    }

    @Test
    fun `demoteFromMaximized on a non-maximized panel is a no-op`() {
        val panel = fullPanel()
        assertEquals(PanelState.NORMAL, panel.state)

        panel.demoteFromMaximized()
        assertEquals(PanelState.NORMAL, panel.state)
    }

    @Test
    fun `width is normalWidth for NORMAL and MAXIMIZED, MINIMIZED_WIDTH for MINIMIZED`() {
        val panel = fullPanel()
        assertEquals(20, panel.width)

        panel.cycleState(-1) // MINIMIZED
        assertEquals(Panel.MINIMIZED_WIDTH, panel.width)

        panel.cycleState(1) // NORMAL
        panel.cycleState(1) // MAXIMIZED
        assertEquals(20, panel.width, "MAXIMIZED never consults width — the layout supplies that")
    }
}
