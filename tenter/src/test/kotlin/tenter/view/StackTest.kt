package tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer

internal class StackTest {

    /** A [width]x[height] block of rows, each filled with [char] repeated. */
    private fun block(width: Int, height: Int, char: String): View = object : View {
        override fun draw(canvas: Canvas) {
            for (row in 0 until height) canvas.writeString(0, row, char.repeat(width))
        }
    }

    private fun render(stack: Stack, width: Int, height: Int = 20): ScreenBuffer {
        val buffer = ScreenBuffer(width, height)
        stack.draw(Canvas.of(buffer))
        return buffer
    }

    @Test
    fun `stacks two children top to bottom, gutter-separated`() {
        val stack = Stack(listOf(block(4, 2, "A"), block(4, 2, "B")), gutter = 1)

        val buffer = render(stack, width = 4)

        assertEquals("AAAA", buffer.line(0, width = 4))
        assertEquals("AAAA", buffer.line(1, width = 4))
        assertEquals("", buffer.line(2, width = 4))
        // Second child starts one row below the first child's measured height (2 + gutter 1 = 3).
        assertEquals("BBBB", buffer.line(3, width = 4))
        assertEquals("BBBB", buffer.line(4, width = 4))
    }

    @Test
    fun `each child is placed by its own measured height, not a fixed slot`() {
        val stack = Stack(listOf(block(4, 3, "A"), block(4, 1, "B")), gutter = 0)

        val buffer = render(stack, width = 4)

        assertEquals("AAAA", buffer.line(2, width = 4))
        assertEquals("BBBB", buffer.line(3, width = 4))
    }

    @Test
    fun `a child past the bottom of the canvas is skipped`() {
        val stack = Stack(listOf(block(4, 5, "A"), block(4, 5, "B"), block(4, 5, "C")), gutter = 0)

        val buffer = render(stack, width = 4, height = 10)

        assertEquals("AAAA", buffer.line(0, width = 4))
        assertEquals("BBBB", buffer.line(5, width = 4))
        // C would start at row 10, at/past the 10-tall canvas — skipped, not drawn out of bounds.
        for (row in 0 until 10) {
            org.junit.jupiter.api.Assertions.assertTrue(
                buffer.line(row, width = 4) != "CCCC",
                "row $row unexpectedly shows C",
            )
        }
    }

    @Test
    fun `a child taller than what remains is clipped, not stretched`() {
        val stack = Stack(listOf(block(4, 8, "A"), block(4, 8, "B")), gutter = 0)

        val buffer = render(stack, width = 4, height = 10)

        assertEquals("AAAA", buffer.line(7, width = 4))
        // B starts at row 8, but the canvas is only 10 tall — only rows 8-9 of B fit.
        assertEquals("BBBB", buffer.line(8, width = 4))
        assertEquals("BBBB", buffer.line(9, width = 4))
    }

    @Test
    fun `a single child measures to its own content height`() {
        val stack = Stack(listOf(block(4, 3, "A")))

        val buffer = render(stack, width = 10, height = 10)

        assertEquals("AAAA", buffer.line(2, width = 4))
        assertEquals("", buffer.line(3, width = 4))
    }
}
