package tenter.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer
import tenter.view.ContentView
import tenter.view.ScrollOffset
import tenter.view.contentView

private enum class SetPanelId : PanelId { MAIN, A, B }

internal class PanelSetTest {

    private fun stubView(lines: Int = 40): ContentView = contentView { cursor ->
        repeat(lines) { row -> cursor.writeLine("row$row") }
    }

    private fun mainPanel() = Panel<SetPanelId, Unit>(
        id = SetPanelId.MAIN,
        title = "MAIN",
        normal = { Panel.Presentation(stubView(), 0) },
    )

    private fun sidePanel(id: SetPanelId, builds: (() -> Unit)? = null) = Panel<SetPanelId, Unit>(
        id = id,
        title = id.name,
        normal = {
            builds?.invoke()
            Panel.Presentation(stubView(), 20)
        },
        minimized = { Panel.Presentation(stubView(1), Panel.MINIMIZED_WIDTH) },
        maximized = { Panel.Presentation(stubView(), 20) },
    )

    private fun render(
        set: PanelSet<SetPanelId, Unit>,
        visible: Set<SetPanelId>,
        width: Int = 80,
        height: Int = 24,
    ): PanelLayout<SetPanelId> = set.render(
        Canvas.of(ScreenBuffer(width, height)),
        Unit,
        visible,
        reservedTop = 0,
    )

    @Test
    fun `initial focus is main and named operations own state changes`() {
        val set = PanelSet.mainAndSides(mainPanel(), listOf(sidePanel(SetPanelId.A)))

        assertEquals(SetPanelId.MAIN, set.focused)
        set.focusOrCycle(SetPanelId.A)
        set.focusOrCycle(SetPanelId.A)

        assertEquals(SetPanelId.A, set.focused)
        assertEquals(PanelState.MAXIMIZED, set.stateOf(SetPanelId.A))
    }

    @Test
    fun `unknown commands and queries are no-ops and null observations`() {
        val set = PanelSet.mainAndSides(mainPanel(), listOf(sidePanel(SetPanelId.A)))

        set.focus(SetPanelId.B)
        set.scroll(SetPanelId.B, 0, 2)
        set.requestRecenter(SetPanelId.B)

        assertEquals(SetPanelId.MAIN, set.focused)
        assertNull(set.stateOf(SetPanelId.B))
        assertNull(set.offsetOf(SetPanelId.B))
    }

    @Test
    fun `focusing another panel demotes a maximized panel to its recorded state`() {
        val a = sidePanel(SetPanelId.A)
        val set = PanelSet.mainAndSides(mainPanel(), listOf(a, sidePanel(SetPanelId.B)))
        set.focus(SetPanelId.A)
        set.cycleFocusedState(-1)
        set.cycleFocusedState(-1)

        set.focus(SetPanelId.B)

        assertEquals(PanelState.MINIMIZED, set.stateOf(SetPanelId.A))
    }

    @Test
    fun `a full cycle while maximized does not replace the restore state`() {
        val a = sidePanel(SetPanelId.A)
        val set = PanelSet.uniform(listOf(a, sidePanel(SetPanelId.B)))
        set.focus(SetPanelId.A)
        set.cycleFocusedState(-1)
        set.cycleFocusedState(-1)
        set.cycleFocusedState(3)
        set.focus(SetPanelId.B)

        assertEquals(PanelState.MINIMIZED, set.stateOf(SetPanelId.A))
    }

    @Test
    fun `at most one panel is maximized after focus and cycle transitions`() {
        val a = sidePanel(SetPanelId.A)
        val b = sidePanel(SetPanelId.B)
        val set = PanelSet.uniform(listOf(a, b))

        set.focus(SetPanelId.A)
        set.cycleFocusedState(1)
        set.focus(SetPanelId.B)
        set.cycleFocusedState(1)

        assertEquals(PanelState.NORMAL, set.stateOf(SetPanelId.A))
        assertEquals(PanelState.MAXIMIZED, set.stateOf(SetPanelId.B))
    }

    @Test
    fun `hidden panels retain state and empty uniform visibility clears focus`() {
        val a = sidePanel(SetPanelId.A)
        val set = PanelSet.uniform(listOf(a))
        set.cycleFocusedState(1)

        render(set, emptySet())

        assertNull(set.focused)
        assertEquals(PanelState.MAXIMIZED, set.stateOf(SetPanelId.A))
        render(set, setOf(SetPanelId.A))
        assertEquals(SetPanelId.A, set.focused)
        assertEquals(PanelState.MAXIMIZED, set.stateOf(SetPanelId.A))
    }

    @Test
    fun `offset is null before a frame and pending scroll does not change the settled snapshot`() {
        val set = PanelSet.uniform(listOf(sidePanel(SetPanelId.A)))
        assertNull(set.offsetOf(SetPanelId.A))

        render(set, setOf(SetPanelId.A))
        assertEquals(ScrollOffset.ZERO, set.offsetOf(SetPanelId.A))
        set.scrollFocused(0, 3)

        assertEquals(ScrollOffset.ZERO, set.offsetOf(SetPanelId.A))
        render(set, setOf(SetPanelId.A))
        assertEquals(ScrollOffset(y = 3), set.offsetOf(SetPanelId.A))
    }

    @Test
    fun `each rendered presentation builder runs once`() {
        var builds = 0
        val panel = sidePanel(SetPanelId.A) { builds++ }
        val set = PanelSet.uniform(listOf(panel))

        render(set, setOf(SetPanelId.A))

        assertEquals(1, builds)
    }

    @Test
    fun `uniform and main-and-sides factories copy inputs and validate capabilities`() {
        val panel = sidePanel(SetPanelId.A)
        val input = mutableListOf(panel)
        val set = PanelSet.uniform(input)
        input.clear()

        assertEquals(listOf(panel), set.sides)

        val invalidMain = sidePanel(SetPanelId.B)
        assertThrows(IllegalArgumentException::class.java) {
            PanelSet.mainAndSides(invalidMain, emptyList())
        }
        assertFalse(invalidMain.states == listOf(PanelState.NORMAL))
    }

    @Test
    fun `duplicate ids and instances fail before attachment`() {
        val duplicateIdA = sidePanel(SetPanelId.A)
        val duplicateIdB = sidePanel(SetPanelId.A)
        assertThrows(IllegalArgumentException::class.java) {
            PanelSet.uniform(listOf(duplicateIdA, duplicateIdB))
        }

        assertThrows(IllegalArgumentException::class.java) {
            PanelSet.uniform(listOf(duplicateIdA, duplicateIdA))
        }
        val fresh = sidePanel(SetPanelId.B)
        assertThrows(IllegalArgumentException::class.java) {
            PanelSet.uniform(listOf(fresh, sidePanel(SetPanelId.B)))
        }
        assertSame(fresh, PanelSet.uniform(listOf(fresh)).sides.single())
    }

    @Test
    fun `a panel cannot be attached to two sets`() {
        val panel = sidePanel(SetPanelId.A)
        PanelSet.uniform(listOf(panel))

        val error = assertThrows(IllegalArgumentException::class.java) {
            PanelSet.uniform(listOf(panel))
        }

        assertEquals("Panel A is already attached to a PanelSet", error.message)
    }

    @Test
    fun `uniform layout can render with no visible panels`() {
        val set = PanelSet.uniform(listOf(sidePanel(SetPanelId.A)))

        val layout = render(set, emptySet())

        assertNull(layout.main)
        assertEquals(emptyList<PanelLayout.Slot<SetPanelId>>(), layout.sides)
    }

    @Test
    fun `hit testing uses settled content geometry and ignores pending scroll`() {
        val panel = sidePanel(SetPanelId.A)
        val set = PanelSet.uniform(listOf(panel))

        val first = render(set, setOf(SetPanelId.A), width = 40, height = 12)
        val slot = first.sides.single()
        assertNull(set.hitTest(slot.content.x, slot.content.y)?.contentPoint)
        assertEquals(
            tenter.screen.Point(0, 0),
            set.hitTest(slot.content.x, slot.content.y + 1)?.contentPoint,
        )

        set.scroll(SetPanelId.A, 0, 3)
        assertEquals(
            tenter.screen.Point(0, 0),
            set.hitTest(slot.content.x, slot.content.y + 1)?.contentPoint,
            "queued scroll must not change the displayed frame's mapping",
        )

        val scrolled = render(set, setOf(SetPanelId.A), width = 40, height = 12).sides.single()
        assertEquals(
            tenter.screen.Point(0, 3),
            set.hitTest(scrolled.content.x, scrolled.content.y + 1)?.contentPoint,
        )
        assertTrue(set.hitTest(scrolled.outer.x - 1, scrolled.outer.y) == null)
    }

    @Test
    fun `hit testing identifies border and main panel without content point`() {
        val set = PanelSet.mainAndSides(mainPanel(), listOf(sidePanel(SetPanelId.A)))
        val layout = set.render(Canvas.of(ScreenBuffer(60, 12)), Unit, setOf(SetPanelId.A), reservedTop = 2)
        val main = checkNotNull(layout.main)

        assertEquals(SetPanelId.MAIN, set.hitTest(main.outer.x, main.outer.y)?.id)
        assertNull(set.hitTest(main.outer.x, main.outer.y)?.contentPoint)
        assertEquals(SetPanelId.A, set.panelAt(layout.sides.single().outer.x, layout.sides.single().outer.y))
    }
}
