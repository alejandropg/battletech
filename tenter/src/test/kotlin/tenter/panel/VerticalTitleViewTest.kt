package tenter.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.view.line
import tenter.view.render

internal class VerticalTitleViewTest {

    @Test
    fun `draws no border cells — content only`() {
        val view = VerticalTitleView("LOG")

        val buffer = render(view, 7, 10)

        val topRow = buffer.line(0, 0, 7)
        assert(!topRow.contains("╭") && !topRow.contains("╮")) { "Should draw no border, got: $topRow" }
    }

    @Test
    fun `renders vertical title chars centred`() {
        val view = VerticalTitleView("LOG")

        val buffer = render(view, 7, 10)

        val cx = 3 // centre for width=7
        assertEquals("L", buffer.get(cx, 0).char)
        assertEquals("O", buffer.get(cx, 1).char)
        assertEquals("G", buffer.get(cx, 2).char)
        assertEquals(" ", buffer.get(cx, 3).char)
    }

    @Test
    fun `renders a multi-word title with space as blank row`() {
        val view = VerticalTitleView("UNIT STATUS")

        val buffer = render(view, 7, 15)

        val cx = 3
        assertEquals("U", buffer.get(cx, 0).char)
        assertEquals("N", buffer.get(cx, 1).char)
        assertEquals("I", buffer.get(cx, 2).char)
        assertEquals("T", buffer.get(cx, 3).char)
        // row 4 = space -> blank
        assertEquals(" ", buffer.get(cx, 4).char)
        assertEquals("S", buffer.get(cx, 5).char)
        assertEquals("T", buffer.get(cx, 6).char)
        assertEquals("A", buffer.get(cx, 7).char)
        assertEquals("T", buffer.get(cx, 8).char)
        assertEquals("U", buffer.get(cx, 9).char)
        assertEquals("S", buffer.get(cx, 10).char)
    }

    @Test
    fun `clips title that exceeds panel height without throwing`() {
        val view = VerticalTitleView("ABCDEFGHIJKLMNOPQRST")

        val buffer = render(view, 7, 3)

        val cx = 3
        assertEquals("A", buffer.get(cx, 0).char)
        assertEquals("B", buffer.get(cx, 1).char)
        assertEquals("C", buffer.get(cx, 2).char)
    }
}
