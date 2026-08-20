package tenter.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer
import tenter.view.ScrollOffset
import tenter.view.View

private enum class SetPanelId(override val badge: Char) : PanelId { MAIN('0'), A('a'), B('b') }

internal class PanelSetTest {

    private fun stubView(lines: Int = 40): View = object : View {
        override fun draw(canvas: Canvas) {
            for (i in 0 until lines) canvas.writeString(0, i, "row$i")
        }
    }

    private fun mainPanel() = Panel<SetPanelId, Unit>(
        id = SetPanelId.MAIN,
        title = "MAIN",
        normalWidth = 0,
        normal = { stubView() },
    )

    private fun sidePanel(id: SetPanelId) = Panel<SetPanelId, Unit>(
        id = id,
        title = id.name,
        normalWidth = 20,
        normal = { stubView() },
        minimized = { stubView(1) },
        maximized = { stubView() },
    )

    private fun render(set: PanelSet<SetPanelId, Unit>, visible: Set<SetPanelId>, width: Int = 80, height: Int = 24) {
        val canvas = Canvas.of(ScreenBuffer(width, height))
        set.render(canvas, Unit, visible, reservedTop = 0)
    }

    @Test
    fun `initial focus is main`() {
        val set = PanelSet(mainPanel(), listOf(sidePanel(SetPanelId.A), sidePanel(SetPanelId.B)))

        assertEquals(SetPanelId.MAIN, set.focused)
    }

    @Test
    fun `focus moves focus to a known panel`() {
        val set = PanelSet(mainPanel(), listOf(sidePanel(SetPanelId.A), sidePanel(SetPanelId.B)))

        set.focus(SetPanelId.A)

        assertEquals(SetPanelId.A, set.focused)
    }

    @Test
    fun `focus on an unknown id is a no-op`() {
        val set = PanelSet(mainPanel(), listOf(sidePanel(SetPanelId.A)))

        set.focus(SetPanelId.B) // B isn't in this set

        assertEquals(SetPanelId.MAIN, set.focused)
    }

    @Test
    fun `focusing another panel demotes the maximized one to its recorded state`() {
        val a = sidePanel(SetPanelId.A)
        val set = PanelSet(mainPanel(), listOf(a, sidePanel(SetPanelId.B)))
        set.focus(SetPanelId.A)
        a.cycleState(-1) // NORMAL -> MINIMIZED
        a.cycleState(-1) // MINIMIZED -> MAXIMIZED (wrap), recording MINIMIZED as the restore state

        set.focus(SetPanelId.B)

        assertEquals(PanelState.MINIMIZED, a.state, "A must fall back to MINIMIZED, not NORMAL")
    }

    @Test
    fun `at most one panel is MAXIMIZED after any sequence of focus and cycle calls`() {
        val a = sidePanel(SetPanelId.A)
        val b = sidePanel(SetPanelId.B)
        val set = PanelSet(mainPanel(), listOf(a, b))

        set.focus(SetPanelId.A)
        set.cycleFocusedState(1) // A -> MAXIMIZED
        set.focus(SetPanelId.B)
        set.cycleFocusedState(1) // B -> MAXIMIZED

        assertEquals(PanelState.NORMAL, a.state, "A must have been demoted when B was focused")
        assertEquals(PanelState.MAXIMIZED, b.state)
    }

    @Test
    fun `a focused side panel dropping out of visible at render time demotes and returns focus to main`() {
        val a = sidePanel(SetPanelId.A)
        val b = sidePanel(SetPanelId.B)
        val set = PanelSet(mainPanel(), listOf(a, b))
        set.focus(SetPanelId.A)
        a.cycleState(1) // NORMAL -> MAXIMIZED

        render(set, visible = setOf(SetPanelId.B)) // A is no longer visible this frame

        assertEquals(SetPanelId.MAIN, set.focused)
        assertEquals(PanelState.NORMAL, a.state, "A must be demoted out of MAXIMIZED even though hidden")
    }

    @Test
    fun `panelIdAt never returns main`() {
        val set = PanelSet(mainPanel(), listOf(sidePanel(SetPanelId.A)))
        render(set, visible = setOf(SetPanelId.A), width = 40, height = 10)

        // The main panel occupies the left region; the side panel occupies the right 20 columns.
        assertNull(set.panelIdAt(0, 0), "main slot must never be returned")
        assertEquals(SetPanelId.A, set.panelIdAt(39, 0))
    }

    @Test
    fun `offsetOf reflects the settled offset`() {
        val a = sidePanel(SetPanelId.A)
        val set = PanelSet(mainPanel(), listOf(a))
        set.focus(SetPanelId.A)
        set.scrollFocused(0, 2)

        render(set, visible = setOf(SetPanelId.A))

        assertEquals(ScrollOffset(y = 2), set.offsetOf(SetPanelId.A))
    }

    @Test
    fun `scrollFocused moves only the focused panel`() {
        val a = sidePanel(SetPanelId.A)
        val b = sidePanel(SetPanelId.B)
        val set = PanelSet(mainPanel(), listOf(a, b))
        set.focus(SetPanelId.A)

        set.scrollFocused(0, 3)

        assertEquals(ScrollOffset(y = 3), a.offset)
        assertEquals(ScrollOffset.ZERO, b.offset)
    }
}
