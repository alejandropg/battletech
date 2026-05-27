package battletech.tui.view

import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CollapsedPanelViewTest {

    private fun readLine(buffer: ScreenBuffer, x: Int, y: Int, width: Int): String =
        (x until x + width).map { buffer.get(it, y).char }.joinToString("").trimEnd()

    @Test
    fun `renders border corners and index label for LOG panel`() {
        val view = CollapsedPanelView(0, "LOG")
        val buffer = ScreenBuffer(7, 10)

        view.render(buffer, 0, 0, 7, 10)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(6, 0).char)
        val topRow = readLine(buffer, 0, 0, 7)
        assert(topRow.contains("[0]")) { "Top row should contain '[0]', got: $topRow" }
    }

    @Test
    fun `renders vertical title chars centred for LOG panel`() {
        val view = CollapsedPanelView(0, "LOG")
        val buffer = ScreenBuffer(7, 10)

        view.render(buffer, 0, 0, 7, 10)

        // Centre column for width=7 is x + 1 + (7-2)/2 = 0 + 1 + 2 = 3
        assertEquals("L", buffer.get(3, 1).char)
        assertEquals("O", buffer.get(3, 2).char)
        assertEquals("G", buffer.get(3, 3).char)
        // Rows 4-8 (inner) should be blank at centre
        assertEquals(" ", buffer.get(3, 4).char)
        assertEquals(" ", buffer.get(3, 8).char)
    }

    @Test
    fun `renders UNIT STATUS title with space as blank row`() {
        val view = CollapsedPanelView(1, "UNIT STATUS")
        val buffer = ScreenBuffer(7, 15)

        view.render(buffer, 0, 0, 7, 15)

        val cx = 3 // centre for width=7
        assertEquals("U", buffer.get(cx, 1).char)
        assertEquals("N", buffer.get(cx, 2).char)
        assertEquals("I", buffer.get(cx, 3).char)
        assertEquals("T", buffer.get(cx, 4).char)
        // row 5 = space → blank
        assertEquals(" ", buffer.get(cx, 5).char)
        assertEquals("S", buffer.get(cx, 6).char)
        assertEquals("T", buffer.get(cx, 7).char)
        assertEquals("A", buffer.get(cx, 8).char)
        assertEquals("T", buffer.get(cx, 9).char)
        assertEquals("U", buffer.get(cx, 10).char)
        assertEquals("S", buffer.get(cx, 11).char)
        // rows 12-13 blank inside (row 14 is bottom border)
        assertEquals(" ", buffer.get(cx, 12).char)
        assertEquals(" ", buffer.get(cx, 13).char)
    }

    @Test
    fun `clips title that exceeds panel height without throwing`() {
        // Title of 20 chars in a panel of height 5 (only rows 1-3 are inner rows)
        val view = CollapsedPanelView(2, "ABCDEFGHIJKLMNOPQRST")
        val buffer = ScreenBuffer(7, 5)

        view.render(buffer, 0, 0, 7, 5)

        val cx = 3
        assertEquals("A", buffer.get(cx, 1).char)
        assertEquals("B", buffer.get(cx, 2).char)
        assertEquals("C", buffer.get(cx, 3).char)
        // Row 4 is bottom border — must not be overwritten with 'D'
        assertEquals("╰", buffer.get(0, 4).char)
    }
}
