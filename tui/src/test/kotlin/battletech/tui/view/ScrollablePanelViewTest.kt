package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ScrollablePanelViewTest {

    private fun stubContent(lines: Int): View = object : View {
        override fun render(canvas: Canvas) {
            for (i in 0 until lines) {
                canvas.writeString(0, i, "line$i")
            }
        }
    }

    @Test
    fun `draws box and title at given coordinates`() {
        val view = ScrollablePanelView(key = '2', title = "FOO", content = stubContent(0), scrollOffset = 0)

        val buffer = render(view, 30, 10)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(29, 0).char)
        assertEquals("[", buffer.get(2, 0).char)
        assertEquals("2", buffer.get(3, 0).char)
        assertEquals("]", buffer.get(4, 0).char)
        assertEquals("F", buffer.get(6, 0).char)
        assertEquals("O", buffer.get(7, 0).char)
        assertEquals("O", buffer.get(8, 0).char)
    }

    @Test
    fun `content at offset 0 starts at row y+2 col x+2`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(5), scrollOffset = 0)

        val buffer = render(view, 30, 10)

        assertEquals("line0", buffer.line(2, 2, 10))
        assertEquals("line1", buffer.line(3, 2, 10))
    }

    @Test
    fun `explicit offset shifts the visible window`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(20), scrollOffset = 3)

        val buffer = render(view, 30, 10)

        assertEquals("line3", buffer.line(2, 2, 10))
        assertEquals("line4", buffer.line(3, 2, 10))
    }

    @Test
    fun `offset beyond maxOffset is clamped to maxOffset`() {
        val content = stubContent(5)
        val view = ScrollablePanelView(key = '0', title = "T", content = content, scrollOffset = 999)

        val buffer = render(view, 30, 10)

        val viewportHeight = 7
        val maxOffset = maxOf(0, 5 - viewportHeight)
        assertEquals("line${maxOffset}", buffer.line(2, 2, 10))
    }

    @Test
    fun `maxOffset property equals contentHeight minus viewportHeight clamped to zero`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(20), scrollOffset = 0)

        render(view, 30, 10)

        val viewportHeight = 7
        assertEquals(20 - viewportHeight, view.maxOffset)
    }

    @Test
    fun `maxOffset is zero when content fits in viewport`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(3), scrollOffset = 0)

        render(view, 30, 10)

        assertEquals(0, view.maxOffset)
    }

    @Test
    fun `null scrollOffset with anchorBottom false shows top of content`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(20), scrollOffset = null, anchorBottom = false)

        val buffer = render(view, 30, 10)

        assertEquals("line0", buffer.line(2, 2, 10))
    }

    @Test
    fun `null scrollOffset with anchorBottom true shows bottom of content`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(20), scrollOffset = null, anchorBottom = true)

        val buffer = render(view, 30, 10)

        val viewportHeight = 7
        val maxOffset = 20 - viewportHeight
        assertEquals("line${maxOffset}", buffer.line(2, 2, 10))
    }

    @Test
    fun `scrollbar block cells appear on right border only when content overflows`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(20), scrollOffset = 0)

        val buffer = render(view, 30, 10)

        // Row 1 is the padding spacer row, never part of the content viewport, so it never
        // carries the thumb even while content overflows.
        val thumbRange = Scrollbar.thumb(track = 7, contentHeight = 20, viewportHeight = 7, offset = 0)!!
        for (row in 1..8) {
            val cell = buffer.get(29, row)
            if (row >= 2 && (row - 2) in thumbRange) {
                assertEquals("▐", cell.char, "expected thumb at row $row")
                assertEquals(Color.GREEN, cell.style.fg)
            } else {
                assertEquals("│", cell.char, "expected border at row $row")
            }
        }
    }

    @Test
    fun `no scrollbar cells when content exactly fits viewport`() {
        val viewportHeight = 7
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(viewportHeight), scrollOffset = 0)

        val buffer = render(view, 30, 10)

        for (row in 1..8) {
            assertEquals("│", buffer.get(29, row).char, "expected plain border at row $row")
        }
    }

    @Test
    fun `degenerate height 2 draws only box and never throws`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(5), scrollOffset = 0)

        val buffer = render(view, 30, 2)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╰", buffer.get(0, 1).char)
        assertEquals(0, view.maxOffset)
    }

    @Test
    fun `degenerate width 4 draws only box and never throws`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(5), scrollOffset = 0)

        val buffer = render(view, 4, 10)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals(0, view.maxOffset)
    }

    @Test
    fun `content rendered at offset position within parent buffer`() {
        val view = ScrollablePanelView(key = '0', title = "T", content = stubContent(5), scrollOffset = 0)
        val buffer = ScreenBuffer(40, 20)

        view.render(Canvas.of(buffer).region(5, 3, 20, 10))

        assertEquals("╭", buffer.get(5, 3).char)
        assertEquals("line0", buffer.line(5, 7, 10))
    }
}
