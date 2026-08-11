package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Color
import battletech.tui.screen.Insets
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pixel parity for [Bordered]'s border, title, badge, gutters, and scrollbar-thumb decoration. */
internal class BorderedTest {

    private val blank: View = object : View {
        override fun render(canvas: Canvas) = Unit
    }

    @Test
    fun `renders corners around the whole canvas`() {
        val view = Bordered(blank)

        val buffer = render(view, 10, 5)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(9, 0).char)
        assertEquals("╰", buffer.get(0, 4).char)
        assertEquals("╯", buffer.get(9, 4).char)
    }

    @Test
    fun `renders horizontal and vertical borders`() {
        val view = Bordered(blank)

        val buffer = render(view, 6, 4)

        for (i in 1..4) {
            assertEquals("─", buffer.get(i, 0).char)
            assertEquals("─", buffer.get(i, 3).char)
        }
        for (i in 1..2) {
            assertEquals("│", buffer.get(0, i).char)
            assertEquals("│", buffer.get(5, i).char)
        }
    }

    @Test
    fun `renders title`() {
        val view = Bordered(blank, title = "TEST")

        val buffer = render(view, 20, 3)

        assertEquals(" ", buffer.get(3, 0).char)
        assertEquals("T", buffer.get(4, 0).char)
        assertEquals("E", buffer.get(5, 0).char)
        assertEquals("S", buffer.get(6, 0).char)
        assertEquals("T", buffer.get(7, 0).char)
        assertEquals(" ", buffer.get(8, 0).char)
        assertEquals(Color.ACCENT, buffer.get(4, 0).style.fg)
        assertEquals(Color.PANEL_BORDER, buffer.get(0, 0).style.fg)
    }

    @Test
    fun `uses specified colors`() {
        val view = Bordered(blank, borderColor = Color.DANGER, titleColor = Color.TEXT_PRIMARY)

        val buffer = render(view, 10, 3)

        assertEquals(Color.DANGER, buffer.get(0, 0).style.fg)
    }

    @Test
    fun `renders single-char badge title`() {
        val view = Bordered(blank, title = "TEST", badge = "2")

        val buffer = render(view, 20, 3)

        assertEquals("[", buffer.get(2, 0).char)
        assertEquals("2", buffer.get(3, 0).char)
        assertEquals("]", buffer.get(4, 0).char)
        assertEquals(" ", buffer.get(5, 0).char)
        assertEquals("T", buffer.get(6, 0).char)
        assertEquals("E", buffer.get(7, 0).char)
        assertEquals("S", buffer.get(8, 0).char)
        assertEquals("T", buffer.get(9, 0).char)
        assertEquals(" ", buffer.get(10, 0).char)
        assertEquals(Color.ACCENT, buffer.get(2, 0).style.fg)
        assertEquals(Color.ACCENT, buffer.get(6, 0).style.fg)
    }

    @Test
    fun `renders letter badge title, e g HELP`() {
        val view = Bordered(blank, title = "HELP", badge = "h")

        val buffer = render(view, 20, 3)

        assertEquals("[", buffer.get(2, 0).char)
        assertEquals("h", buffer.get(3, 0).char)
        assertEquals("]", buffer.get(4, 0).char)
        assertEquals(" ", buffer.get(5, 0).char)
        assertEquals("H", buffer.get(6, 0).char)
        assertEquals("E", buffer.get(7, 0).char)
        assertEquals("L", buffer.get(8, 0).char)
        assertEquals("P", buffer.get(9, 0).char)
        assertEquals(" ", buffer.get(10, 0).char)
        assertEquals(Color.ACCENT, buffer.get(3, 0).style.fg)
        assertEquals(Color.ACCENT, buffer.get(6, 0).style.fg)
    }

    @Test
    fun `skips title when box too narrow`() {
        val view = Bordered(blank, title = "TOOLONG")

        val buffer = render(view, 8, 3)

        assertEquals("─", buffer.get(3, 0).char)
    }

    @Test
    fun `renders a badge alone, with no title to anchor it to — e g a collapsed panel's stub`() {
        val view = Bordered(blank, badge = "0")

        val buffer = render(view, 7, 3)

        assertEquals("[", buffer.get(2, 0).char)
        assertEquals("0", buffer.get(3, 0).char)
        assertEquals("]", buffer.get(4, 0).char)
    }

    @Test
    fun `omits a badge that has no room, even alone`() {
        val view = Bordered(blank, badge = "0")

        val buffer = render(view, 5, 3)

        assertEquals("─", buffer.get(3, 0).char, "5-wide box has no room for [0] at column 2")
    }

    @Test
    fun `content renders inside the border, offset by gutters`() {
        val content = object : View {
            override fun render(canvas: Canvas) {
                canvas.writeString(0, 0, "HI")
            }
        }
        val view = Bordered(content, gutters = Insets(left = 2, right = 2))

        val buffer = render(view, 20, 5)

        assertEquals("H", buffer.get(3, 1).char)
        assertEquals("I", buffer.get(4, 1).char)
    }

    @Test
    fun `content rendered at offset position within parent buffer`() {
        val view = Bordered(object : View {
            override fun render(canvas: Canvas) {
                canvas.writeString(0, 0, "X")
            }
        })
        val buffer = ScreenBuffer(40, 20)

        view.render(Canvas.of(buffer).region(5, 3, 20, 10))

        assertEquals("╭", buffer.get(5, 3).char)
        assertEquals("X", buffer.get(6, 4).char)
    }

    // ── scrollbar thumbs, synchronized with a Scrolled ─────────────────────────────────────────

    private fun stubContent(lines: Int): View = object : View {
        override fun render(canvas: Canvas) {
            for (i in 0 until lines) canvas.writeString(0, i, "line$i")
        }
    }

    @Test
    fun `scrollbar block cells appear on right border only when content overflows`() {
        val view = scrollingPanel(title = "T", badge = "0", content = stubContent(20), extent = ContentExtent.Measured())

        val buffer = render(view, 30, 10)

        // The viewport spans the whole inner height (rows 1..8) — the padding row is part of the
        // scrollable stream, not a fixed offset — so the thumb can occupy row 1 too.
        val thumbRange = Scrollbar.thumb(track = 8, contentHeight = 21, viewportHeight = 8, offset = 0)!!
        for (row in 1..8) {
            val cell = buffer.get(29, row)
            if ((row - 1) in thumbRange) {
                assertEquals("▐", cell.char, "expected thumb at row $row")
                assertEquals(Color.PANEL_BORDER, cell.style.fg)
            } else {
                assertEquals("│", cell.char, "expected border at row $row")
            }
        }
    }

    @Test
    fun `no scrollbar cells when content exactly fits viewport`() {
        val viewportHeight = 8
        // The stream includes one extra row for the reclaimable top padding, so
        // `viewportHeight - 1` content lines exactly fill it with no overflow.
        val view = scrollingPanel(title = "T", badge = "0", content = stubContent(viewportHeight - 1), extent = ContentExtent.Measured())

        val buffer = render(view, 30, 10)

        for (row in 1..8) {
            assertEquals("│", buffer.get(29, row).char, "expected plain border at row $row")
        }
    }

    @Test
    fun `a box with no thumbsFrom never draws a thumb, however tall its content`() {
        val view = Bordered(stubContent(20))

        val buffer = render(view, 30, 10)

        for (row in 1..8) {
            assertEquals("│", buffer.get(29, row).char, "expected plain border, no thumb without a Scrolled")
        }
    }
}
