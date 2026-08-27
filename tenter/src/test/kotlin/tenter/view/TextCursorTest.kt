package tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.styled

internal class TextCursorTest {

    @Test
    fun `writeLine paints each span in its own style at the right column`() {
        val canvas = Canvas.offscreen(20, 3)
        val cursor = TextCursor(canvas)

        cursor.writeLine(
            styled {
                append("foo", ChromeRole.DANGER)
                append("bar", ChromeRole.SUCCESS)
            },
        )

        assertEquals(Cell.Style(ChromeRole.DANGER), canvas.get(0, 0).style)
        assertEquals(Cell.Style(ChromeRole.DANGER), canvas.get(2, 0).style)
        assertEquals(Cell.Style(ChromeRole.SUCCESS), canvas.get(3, 0).style)
        assertEquals(Cell.Style(ChromeRole.SUCCESS), canvas.get(5, 0).style)
        assertEquals("f", canvas.get(0, 0).char)
        assertEquals("b", canvas.get(3, 0).char)
    }

    @Test
    fun `writeLine advances a wide codepoint's column by two cells`() {
        val canvas = Canvas.offscreen(20, 3)
        val cursor = TextCursor(canvas)

        cursor.writeLine(
            styled {
                append("中", ChromeRole.DANGER)
                append("A", ChromeRole.SUCCESS)
            },
        )

        assertEquals("中", canvas.get(0, 0).char)
        assertEquals("", canvas.get(1, 0).char, "filler reserves the wide glyph's second cell")
        assertEquals("A", canvas.get(2, 0).char)
        assertEquals(Cell.Style(ChromeRole.SUCCESS), canvas.get(2, 0).style)
    }

    @Test
    fun `writeLine ellipsizes at the canvas width`() {
        val canvas = Canvas.offscreen(5, 1)
        val cursor = TextCursor(canvas)

        cursor.writeLine(styled { append("hello world", ChromeRole.DANGER) })

        assertEquals("hell…", (0 until 5).joinToString("") { canvas.get(it, 0).char })
    }

    @Test
    fun `writeLine advances row by one and returns the row written`() {
        val canvas = Canvas.offscreen(10, 3)
        val cursor = TextCursor(canvas)

        val written = cursor.writeLine(styled { append("a") })

        assertEquals(0, written)
        assertEquals(1, cursor.row)
    }
}
