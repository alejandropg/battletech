package io.archinaut.tenter.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.ScreenBuffer
import io.archinaut.tenter.view.ContentView
import io.archinaut.tenter.view.ScrollOffset
import io.archinaut.tenter.view.contentView
import io.archinaut.tenter.view.fixedContent
import io.archinaut.tenter.view.View

private enum class SetPanelId : PanelId { MAIN, A, B }

internal class PanelSetTest {

    @Test
    fun `multiple hidden panels retain independent recenter requests until drawable`() {
        val ids = setOf(SetPanelId.A, SetPanelId.B)
        val set = PanelSet.uniform(ids.map { id ->
            Panel<SetPanelId, Unit>(id, id.name, normal = {
                Panel.Presentation.allocated(contentView { cursor ->
                    repeat(100) { cursor.writeLine("row$it") }
                    cursor.markRevealAt(40)
                })
            })
        })
        render(set, ids)
        ids.forEach { set.scroll(it, 0, -10) }
        val panned = render(set, ids)
        ids.forEach { assertEquals(10, panned.offsetOf(it).y) }
        ids.forEach(set::requestRecenter)

        assertTrue(render(set, emptySet()).sides.isEmpty())
        assertTrue(render(set, ids, width = 4).sides.all { it.scroll == null })
        val recentered = render(set, ids)

        ids.forEach { assertEquals(31, recentered.offsetOf(it).y) }
        ids.forEach { set.scroll(it, 0, -3) }
        val next = render(set, ids)
        ids.forEach { assertEquals(28, next.offsetOf(it).y) }
    }

    @Test
    fun `nondrawable slots publish no stale snapshot but retain pending scroll for recovery`() {
        for ((width, height) in listOf(4 to 12, 40 to 2, 0 to 0)) {
            val set = PanelSet.uniform(listOf(sidePanel(SetPanelId.A)))
            val visible = setOf(SetPanelId.A)
            render(set, visible, width = 40, height = 12)
            set.scrollFocused(0, 3)
            val previous = render(set, visible, width = 40, height = 12)
            set.scrollFocused(0, 2)

            val tiny = render(set, visible, width, height)

            assertNull(tiny.sides.single().scroll)
            assertNull(set.hitTest(2, 2)?.contentPoint)
            assertEquals(3, previous.offsetOf(SetPanelId.A).y)
            val restored = render(set, visible, width = 40, height = 12)
            assertEquals(5, restored.offsetOf(SetPanelId.A).y)
            assertEquals(36, checkNotNull(restored.sides.single().scroll).viewportWidth)
        }
    }

    @Test
    fun `focused commands affect the retained hidden panel and become visible on return`() {
        val set = PanelSet.uniform(listOf(sidePanel(SetPanelId.A), sidePanel(SetPanelId.B)))
        val visible = setOf(SetPanelId.A, SetPanelId.B)
        set.focus(SetPanelId.B)
        render(set, visible)
        render(set, emptySet())

        set.scrollFocused(0, 3)
        set.pageFocused(1)
        set.cycleFocusedState(1)

        assertEquals(SetPanelId.B, set.focused)
        assertEquals(PanelState.NORMAL, set.stateOf(SetPanelId.A))
        assertEquals(PanelState.MAXIMIZED, set.stateOf(SetPanelId.B))
        assertNull(set.hitTest(2, 2))
        val returned = render(set, visible)
        assertEquals(SetPanelId.B, returned.sides.single().id)
        // With no displayed viewport, a page request retains the existing one-row fallback.
        assertEquals(4, returned.offsetOf(SetPanelId.B).y)
    }

    @Test
    fun `failed later paint retains all offsets and queued scrolling and recenter until retry`() {
        var fail = false
        val a = Panel<SetPanelId, Unit>(SetPanelId.A, "A", normal = {
            Panel.Presentation.allocated(contentView { cursor ->
                repeat(100) { cursor.writeLine("row$it") }
                cursor.markRevealAt(40)
            })
        })
        val b = Panel<SetPanelId, Unit>(SetPanelId.B, "B", normal = {
            Panel.Presentation.allocated(fixedContent(20, 100, object : View {
                public override fun draw(canvas: Canvas) { check(!fail) }
            }))
        })
        val set = PanelSet.uniform(listOf(a, b))
        val visible = setOf(SetPanelId.A, SetPanelId.B)
        render(set, visible)
        set.scroll(SetPanelId.A, 0, -10)
        val previous = render(set, visible)
        val oldOffset = previous.offsetOf(SetPanelId.A)
        val oldHit = set.hitTest(2, 2)
        val oldBHit = set.hitTest(42, 2)
        set.requestRecenter(SetPanelId.A)
        set.scroll(SetPanelId.B, 0, 5)
        fail = true

        assertThrows(IllegalStateException::class.java) { render(set, visible) }

        assertEquals(oldOffset, previous.offsetOf(SetPanelId.A))
        assertEquals(oldBHit, set.hitTest(42, 2))
        assertEquals(oldHit, set.hitTest(2, 2))
        fail = false
        val retried = render(set, visible)
        val centered = retried.offsetOf(SetPanelId.A)
        assertTrue(centered != oldOffset)
        assertEquals(5, retried.offsetOf(SetPanelId.B).y)
        set.scroll(SetPanelId.A, 0, -3)
        val scrolled = render(set, visible)
        assertEquals(centered.y - 3, scrolled.offsetOf(SetPanelId.A).y)
        assertEquals(5, scrolled.offsetOf(SetPanelId.B).y)
    }

    @Test
    fun `allocated presentation cannot silently collapse a fixed side`() {
        val side = Panel<SetPanelId, Unit>(SetPanelId.A, "A", normal = {
            Panel.Presentation.allocated(stubView())
        })
        val set = PanelSet.mainAndSides(mainPanel(), listOf(side))

        assertThrows(IllegalArgumentException::class.java) { render(set, setOf(SetPanelId.A)) }
        assertNull(set.hitTest(2, 2))
    }

    @Test
    fun `a published layout cannot be altered and does not change the set's own hits`() {
        val a = sidePanel(SetPanelId.A)
        val set = PanelSet.mainAndSides(mainPanel(), listOf(a, sidePanel(SetPanelId.B)))
        val layout = render(set, setOf(SetPanelId.A, SetPanelId.B))
        val hit = set.hitTest(42, 2)

        assertThrows(UnsupportedOperationException::class.java) { (layout.sides as MutableList).clear() }

        assertEquals(hit, set.hitTest(42, 2))
        assertEquals(SetPanelId.A, hit?.id)
        assertThrows(IllegalArgumentException::class.java) { PanelSet.uniform(listOf(a)) }
    }

    @Test
    fun `failed first or later panel paints preserve completed hits and paging geometry`() {
        for (failedId in listOf(SetPanelId.A, SetPanelId.B)) {
            var fail = false
            val panels = listOf(SetPanelId.A, SetPanelId.B).map { id ->
                Panel<SetPanelId, Unit>(id, id.name, normal = {
                    Panel.Presentation.allocated(fixedContent(30, 100, object : View {
                        override fun draw(canvas: Canvas) {
                            if (fail && id == failedId) error("paint failed")
                            canvas.writeString(0, 0, "content")
                        }
                    }))
                })
            }
            val set = PanelSet.uniform(panels)
            val visible = setOf(SetPanelId.A, SetPanelId.B)
            render(set, visible)
            val oldHit = set.hitTest(2, 2)
            set.scroll(SetPanelId.A, 0, 5)
            fail = true

            assertThrows(IllegalStateException::class.java) { render(set, visible, width = 40, height = 10) }

            assertEquals(oldHit, set.hitTest(2, 2))
            set.pageFocused(1)
            fail = false
            assertEquals(27, render(set, visible).offsetOf(SetPanelId.A).y)
        }
    }

    @Test
    fun `a failed initial paint publishes no geometry and can be retried`() {
        var fail = true
        val panel = Panel<SetPanelId, Unit>(SetPanelId.A, "A", normal = {
            Panel.Presentation.allocated(fixedContent(10, 10, object : View {
                override fun draw(canvas: Canvas) { check(!fail) }
            }))
        })
        val set = PanelSet.uniform(listOf(panel))

        assertThrows(IllegalStateException::class.java) { render(set, setOf(SetPanelId.A)) }
        assertNull(set.panelAt(2, 2))
        assertNull(set.hitTest(2, 2))
        fail = false
        render(set, setOf(SetPanelId.A))

        assertEquals(SetPanelId.A, set.hitTest(2, 2)?.id)
    }

    @Test
    fun `uniform defaults share only visible proportional slots`() {
        val set = PanelSet.uniform(listOf(sidePanel(SetPanelId.A), sidePanel(SetPanelId.B)))

        assertEquals(80, render(set, setOf(SetPanelId.A)).sides.single().outer.width)
        set.cycleFocusedState(-1)
        val layout = render(set, setOf(SetPanelId.A, SetPanelId.B))

        assertEquals(listOf(7, 73), layout.sides.map { it.outer.width })
    }

    @Test
    fun `uniform construction owns reserved columns and copied fixed configuration`() {
        val fixed = mutableSetOf(SetPanelId.B)
        val set = PanelSet.uniform(
            listOf(sidePanel(SetPanelId.A), sidePanel(SetPanelId.B)),
            reservedColumns = 3,
            fixedWidthPanels = fixed,
        )
        fixed.clear()

        val layout = render(set, setOf(SetPanelId.A, SetPanelId.B))
        assertEquals(listOf(20, 20), layout.sides.map { it.outer.width })
        assertEquals(listOf(0, 60), layout.sides.map { it.outer.x })
        set.cycleFocusedState(-1)
        val minimized = render(set, setOf(SetPanelId.A, SetPanelId.B))

        assertEquals(listOf(7, 20), minimized.sides.map { it.outer.width })
        assertEquals(60, minimized.sides.last().outer.x)
    }

    @Test
    fun `invalid uniform options fail before claiming panels`() {
        val panels = listOf(sidePanel(SetPanelId.A), sidePanel(SetPanelId.B))

        assertThrows(IllegalArgumentException::class.java) { PanelSet.uniform(panels, reservedColumns = -1) }
        assertThrows(IllegalArgumentException::class.java) { PanelSet.uniform(panels, reservedColumns = 1) }
        assertThrows(IllegalArgumentException::class.java) {
            PanelSet.uniform(panels, fixedWidthPanels = setOf(SetPanelId.MAIN))
        }

        PanelSet.uniform(panels)
    }

    private fun stubView(lines: Int = 40): ContentView = contentView { cursor ->
        repeat(lines) { row -> cursor.writeLine("row$row") }
    }

    private fun mainPanel() = Panel<SetPanelId, Unit>(
        id = SetPanelId.MAIN,
        title = "MAIN",
        normal = { Panel.Presentation.allocated(stubView()) },
    )

    private fun sidePanel(id: SetPanelId, builds: (() -> Unit)? = null) = Panel<SetPanelId, Unit>(
        id = id,
        title = id.name,
        normal = {
            builds?.invoke()
            Panel.Presentation.fixedWidth(stubView(), 20)
        },
        minimized = { Panel.Presentation.fixedWidth(stubView(1), Panel.MINIMIZED_WIDTH) },
        maximized = { Panel.Presentation.allocated(stubView()) },
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
    fun `every id-addressed operation rejects a panel the set does not declare`() {
        val set = PanelSet.mainAndSides(mainPanel(), listOf(sidePanel(SetPanelId.A)))

        assertThrows(IllegalArgumentException::class.java) { set.focus(SetPanelId.B) }
        assertThrows(IllegalArgumentException::class.java) { set.focusOrCycle(SetPanelId.B) }
        assertThrows(IllegalArgumentException::class.java) { set.scroll(SetPanelId.B, 0, 2) }
        assertThrows(IllegalArgumentException::class.java) { set.requestRecenter(SetPanelId.B) }
        assertThrows(IllegalArgumentException::class.java) { set.stateOf(SetPanelId.B) }

        assertEquals(SetPanelId.MAIN, set.focused)
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
    fun `an empty uniform frame retains both hidden panel state and its focus`() {
        val a = sidePanel(SetPanelId.A)
        val set = PanelSet.uniform(listOf(a, sidePanel(SetPanelId.B)))
        set.focus(SetPanelId.B)
        set.cycleFocusedState(1)

        render(set, emptySet())

        assertEquals(SetPanelId.B, set.focused)
        assertEquals(PanelState.MAXIMIZED, set.stateOf(SetPanelId.B))
        render(set, setOf(SetPanelId.A, SetPanelId.B))
        assertEquals(SetPanelId.B, set.focused)
        assertEquals(PanelState.MAXIMIZED, set.stateOf(SetPanelId.B))
    }

    @Test
    fun `a hidden focused panel yields to a visible one`() {
        val set = PanelSet.uniform(listOf(sidePanel(SetPanelId.A), sidePanel(SetPanelId.B)))
        set.focus(SetPanelId.B)

        render(set, setOf(SetPanelId.A))

        assertEquals(SetPanelId.A, set.focused)
    }

    @Test
    fun `pending scroll does not change completed frame observations`() {
        val set = PanelSet.uniform(listOf(sidePanel(SetPanelId.A)))
        assertNull(set.hitTest(2, 2))

        val previous = render(set, setOf(SetPanelId.A))
        val hit = set.hitTest(2, 2)
        assertEquals(ScrollOffset.ZERO, previous.offsetOf(SetPanelId.A))
        set.scrollFocused(0, 3)

        assertEquals(ScrollOffset.ZERO, previous.offsetOf(SetPanelId.A))
        assertEquals(hit, set.hitTest(2, 2))
        val scrolled = render(set, setOf(SetPanelId.A))
        assertEquals(ScrollOffset(y = 3), scrolled.offsetOf(SetPanelId.A))
    }

    private fun PanelLayout<SetPanelId>.offsetOf(id: SetPanelId): ScrollOffset =
        checkNotNull((listOfNotNull(main) + sides).single { it.id == id }.scroll).offset

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

        // Emptying the caller's list must not empty the set: the declaration was copied.
        assertEquals(SetPanelId.A, render(set, setOf(SetPanelId.A)).sides.single().id)

        val invalidMain = sidePanel(SetPanelId.B)
        assertThrows(IllegalArgumentException::class.java) {
            PanelSet.mainAndSides(invalidMain, emptyList())
        }
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
        PanelSet.uniform(listOf(fresh))
        assertThrows(IllegalArgumentException::class.java) { PanelSet.uniform(listOf(fresh)) }
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
            io.archinaut.tenter.screen.Point(0, 0),
            set.hitTest(slot.content.x, slot.content.y + 1)?.contentPoint,
        )

        set.scroll(SetPanelId.A, 0, 3)
        assertEquals(
            io.archinaut.tenter.screen.Point(0, 0),
            set.hitTest(slot.content.x, slot.content.y + 1)?.contentPoint,
            "queued scroll must not change the displayed frame's mapping",
        )

        val scrolled = render(set, setOf(SetPanelId.A), width = 40, height = 12).sides.single()
        assertEquals(
            io.archinaut.tenter.screen.Point(0, 3),
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
