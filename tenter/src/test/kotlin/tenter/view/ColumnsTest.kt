package tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer

internal class ColumnsTest {

    /** A [width]x[height] block of [rows] rows, each filled with [char] repeated. */
    private fun block(width: Int, height: Int, char: String): View = object : View {
        override fun draw(canvas: Canvas) {
            for (row in 0 until height) canvas.writeString(0, row, char.repeat(width))
        }
    }

    private fun render(columns: Columns, width: Int, height: Int = 10): ScreenBuffer {
        val buffer = ScreenBuffer(width, height)
        columns.draw(Canvas.of(buffer))
        return buffer
    }

    @Test
    fun `places two children side by side, gutter-separated`() {
        val columns = Columns(
            listOf(Columns.Child(5, block(5, 2, "A")), Columns.Child(4, block(4, 2, "B"))),
            gutter = 2,
        )

        val buffer = render(columns, width = 40)

        assertEquals("AAAAA  BBBB", buffer.line(0, width = 11))
    }

    @Test
    fun `wraps a child that would not fit to a new band`() {
        val columns = Columns(
            listOf(Columns.Child(6, block(6, 2, "A")), Columns.Child(6, block(6, 3, "B"))),
            gutter = 2,
        )

        // Only room for one 6-wide child plus gutter before the edge.
        val buffer = render(columns, width = 10)

        assertEquals("AAAAAA", buffer.line(0, width = 6))
        assertEquals("", buffer.line(0, x = 6, width = 4))
        // New band starts one row below the tallest child in the previous band (height 2).
        assertEquals("BBBBBB", buffer.line(3, width = 6))
    }

    @Test
    fun `bands stack below the tallest child in the previous band`() {
        val columns = Columns(
            listOf(
                Columns.Child(4, block(4, 5, "A")),
                Columns.Child(4, block(4, 2, "B")),
                Columns.Child(20, block(20, 1, "C")),
            ),
            gutter = 1,
        )

        val buffer = render(columns, width = 9)

        // A and B share the first band (4 + 1 + 4 = 9 fits); tallest is A at 5 rows.
        assertEquals("AAAA", buffer.line(0, width = 4))
        assertEquals("BBBB", buffer.line(0, x = 5, width = 4))
        // C doesn't fit next to B (needs 20), so it starts a new band at row 5 + 1 = 6.
        // The canvas is only 9 wide, so C (declared 20 wide) is clipped, not widened.
        assertEquals("CCCCCCCCC", buffer.line(6, width = 9))
    }

    @Test
    fun `a child wider than the canvas is clipped, not widened`() {
        val columns = Columns(listOf(Columns.Child(20, block(20, 1, "A"))))

        val buffer = render(columns, width = 8)

        assertEquals("AAAAAAAA", buffer.line(0, width = 8))
    }

    @Test
    fun `a single child measures to its own content height`() {
        val columns = Columns(listOf(Columns.Child(4, block(4, 3, "A"))))

        val buffer = render(columns, width = 10, height = 10)

        assertEquals("AAAA", buffer.line(2, width = 4))
        assertEquals("", buffer.line(3, width = 4))
    }
}
