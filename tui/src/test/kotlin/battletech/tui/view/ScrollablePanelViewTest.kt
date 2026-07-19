package battletech.tui.view

import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ScrollablePanelViewTest {

    private fun stubContent(lines: Int): View = object : View {
        override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
            for (i in 0 until lines) {
                buffer.writeString(x, y + i, "line$i")
            }
        }
    }

    private fun readLine(buffer: ScreenBuffer, x: Int, y: Int, width: Int): String =
        (x until x + width).joinToString("") { buffer.get(it, y).char }.trimEnd()

    @Test
    fun `draws box and title at given coordinates`() {
        val view = ScrollablePanelView(index = 2, title = "FOO", content = stubContent(0), scrollOffset = 0)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

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
    fun `content at offset 0 starts at row y+1 col x+2`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(5), scrollOffset = 0)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

        assertEquals("line0", readLine(buffer, 2, 1, 10))
        assertEquals("line1", readLine(buffer, 2, 2, 10))
    }

    @Test
    fun `explicit offset shifts the visible window`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(20), scrollOffset = 3)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

        assertEquals("line3", readLine(buffer, 2, 1, 10))
        assertEquals("line4", readLine(buffer, 2, 2, 10))
    }

    @Test
    fun `offset beyond maxOffset is clamped to maxOffset`() {
        val content = stubContent(5)
        val view = ScrollablePanelView(index = 0, title = "T", content = content, scrollOffset = 999)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

        val viewportHeight = 8
        val maxOffset = maxOf(0, 5 - viewportHeight)
        assertEquals("line${maxOffset}", readLine(buffer, 2, 1, 10))
    }

    @Test
    fun `maxOffset property equals contentHeight minus viewportHeight clamped to zero`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(20), scrollOffset = 0)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

        val viewportHeight = 8
        assertEquals(20 - viewportHeight, view.maxOffset)
    }

    @Test
    fun `maxOffset is zero when content fits in viewport`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(3), scrollOffset = 0)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

        assertEquals(0, view.maxOffset)
    }

    @Test
    fun `null scrollOffset with anchorBottom false shows top of content`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(20), scrollOffset = null, anchorBottom = false)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

        assertEquals("line0", readLine(buffer, 2, 1, 10))
    }

    @Test
    fun `null scrollOffset with anchorBottom true shows bottom of content`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(20), scrollOffset = null, anchorBottom = true)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

        val viewportHeight = 8
        val maxOffset = 20 - viewportHeight
        assertEquals("line${maxOffset}", readLine(buffer, 2, 1, 10))
    }

    @Test
    fun `scrollbar block cells appear on right border only when content overflows`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(20), scrollOffset = 0)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

        val thumbRange = Scrollbar.thumb(track = 8, contentHeight = 20, viewportHeight = 8, offset = 0)!!
        for (row in 1..8) {
            val cell = buffer.get(29, row)
            if (row - 1 in thumbRange) {
                assertEquals("▐", cell.char, "expected thumb at row $row")
                assertEquals(Color.GREEN, cell.style.fg)
            } else {
                assertEquals("│", cell.char, "expected border at row $row")
            }
        }
    }

    @Test
    fun `no scrollbar cells when content exactly fits viewport`() {
        val viewportHeight = 8
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(viewportHeight), scrollOffset = 0)
        val buffer = ScreenBuffer(30, 10)

        view.render(buffer, 0, 0, 30, 10)

        for (row in 1..8) {
            assertEquals("│", buffer.get(29, row).char, "expected plain border at row $row")
        }
    }

    @Test
    fun `degenerate height 2 draws only box and never throws`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(5), scrollOffset = 0)
        val buffer = ScreenBuffer(30, 2)

        view.render(buffer, 0, 0, 30, 2)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╰", buffer.get(0, 1).char)
        assertEquals(0, view.maxOffset)
    }

    @Test
    fun `degenerate width 4 draws only box and never throws`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(5), scrollOffset = 0)
        val buffer = ScreenBuffer(4, 10)

        view.render(buffer, 0, 0, 4, 10)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals(0, view.maxOffset)
    }

    @Test
    fun `content rendered at offset position within parent buffer`() {
        val view = ScrollablePanelView(index = 0, title = "T", content = stubContent(5), scrollOffset = 0)
        val buffer = ScreenBuffer(40, 20)

        view.render(buffer, 5, 3, 20, 10)

        assertEquals("╭", buffer.get(5, 3).char)
        assertEquals("line0", readLine(buffer, 7, 4, 10))
    }
}
