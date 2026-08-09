package battletech.tui.view

import battletech.tui.screen.Canvas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PanelSlotTest {

    private val realView = object : View {
        override fun render(canvas: Canvas) {
            canvas.writeString(0, 0, "CONTENT")
        }
    }

    @Test
    fun `collapsed slot resolves to a CollapsedPanelView carrying the key and title`() {
        val slot = PanelSlot(AttackResultsView.KEY, 7, AttackResultsView.TITLE, collapsed = true) { realView }

        val resolved = resolvePanel(slot)

        assertTrue(resolved is CollapsedPanelView) { "Expected CollapsedPanelView, got $resolved" }
        resolved as CollapsedPanelView
        assertEquals(AttackResultsView.KEY, resolved.key)
        assertEquals(AttackResultsView.TITLE, resolved.title)
    }

    @Test
    fun `expanded slot wraps content in ScrollablePanelView and renders box plus content`() {
        val slot = PanelSlot(
            key = AttackResultsView.KEY,
            width = 34,
            title = AttackResultsView.TITLE,
            collapsed = false,
            scrollOffset = null,
            anchorBottom = false,
        ) { realView }

        val resolved = resolvePanel(slot)

        // Must be a ScrollablePanelView (internal class — verify via rendering)
        val buffer = render(resolved!!, 34, 10)
        // Box: top-left corner present
        assertEquals("╭", buffer.get(0, 0).char)
        // Content "CONTENT" placed at x+2, y+2 (inside box, past the padding spacer row)
        assertEquals("C", buffer.get(2, 2).char)
        assertEquals("O", buffer.get(3, 2).char)
    }

    @Test
    fun `expanded slot with anchorBottom passes flag to ScrollablePanelView`() {
        // A content view with more lines than the viewport; bottom-anchor should show the last line
        val lines = 20
        val contentView = object : View {
            override fun render(canvas: Canvas) {
                for (i in 0 until lines) canvas.writeString(0, i, "L$i")
            }
        }
        val slot = PanelSlot(
            key = '0',
            width = 30,
            title = "T",
            collapsed = false,
            scrollOffset = null,
            anchorBottom = true,
        ) { contentView }

        val resolved = resolvePanel(slot)!!
        val buffer = render(resolved, 30, 10)

        // With anchorBottom=true and null offset, last lines should be visible.
        // viewport height = 10-2 = 8; maxOffset = 21-8 = 13 (the stream carries one extra row
        // for the reclaimable top padding); first visible line = L12, in row 1 — the padding
        // row is reclaimed by content once the panel is scrolled off the top.
        val firstVisible = (0 until 8).map { row ->
            (2 until 8).joinToString("") { col -> buffer.get(col, 1 + row).char }.trimEnd()
        }.first { it.isNotBlank() }
        assertTrue(firstVisible.startsWith("L12"), "Expected first visible line to start with L12 but got: $firstVisible")
    }

    @Test
    fun `expanded slot passes scrollOffset into the wrapper`() {
        val contentView = object : View {
            override fun render(canvas: Canvas) {
                for (i in 0 until 20) canvas.writeString(0, i, "row$i")
            }
        }
        val slot = PanelSlot(
            key = '0',
            width = 30,
            title = "T",
            collapsed = false,
            scrollOffset = 5,
            anchorBottom = false,
        ) { contentView }

        val resolved = resolvePanel(slot)!!
        val buffer = render(resolved, 30, 10)

        // offset=5 → first visible row is row5
        val firstLine = (2 until 8).joinToString("") { buffer.get(it, 2).char }.trimEnd()
        assertEquals("row5", firstLine)
    }

    @Test
    fun `slot with no width resolves to null`() {
        val slot = PanelSlot(AttackResultsView.KEY, 0, AttackResultsView.TITLE, collapsed = true) { realView }

        assertNull(resolvePanel(slot))
    }

    @Test
    fun `buildReal is not invoked for a collapsed slot`() {
        var built = false
        val slot = PanelSlot(AttackResultsView.KEY, 7, AttackResultsView.TITLE, collapsed = true) {
            built = true
            realView
        }

        resolvePanel(slot)

        assertTrue(!built) { "buildReal must not run when the panel is collapsed" }
    }

    @Test
    fun `null buildReal result for expanded slot resolves to null`() {
        val slot = PanelSlot(
            key = '0',
            width = 30,
            title = "T",
            collapsed = false,
            scrollOffset = null,
            anchorBottom = false,
        ) { null }

        assertNull(resolvePanel(slot))
    }
}
