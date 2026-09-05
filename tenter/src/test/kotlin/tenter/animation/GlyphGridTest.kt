package tenter.animation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.ScreenBuffer
import tenter.view.render

internal class GlyphGridTest {

    private val size = AnimationSize(width = 3, height = 2)
    private val priority: (Char) -> Int = { char -> if (char == 'X') 10 else 0 }
    private val style: (Char) -> Cell.Style = { char ->
        if (char == 'X') Cell.Style(fg = ChromeRole.DANGER, bg = ChromeRole.INFO)
        else Cell.Style(fg = ChromeRole.SUCCESS, bg = ChromeRole.TEXT_MUTED)
    }

    private fun grid(priority: (Char) -> Int = this.priority): GlyphGrid = GlyphGrid(size, priority, style)

    @Test
    fun `higher priority wins and equal priority is last write wins`() {
        val glyphs = grid()

        glyphs.put(0, 0, '.')
        glyphs.put(0, 0, 'X')
        glyphs.put(0, 0, '.')
        glyphs.put(1, 0, 'a')
        glyphs.put(1, 0, 'b')

        assertEquals('X', glyphs.get(0, 0))
        assertEquals('b', glyphs.get(1, 0))
    }

    @Test
    fun `set bypasses priority`() {
        val glyphs = grid()

        glyphs.put(0, 0, 'X')
        glyphs.set(0, 0, '.')

        assertEquals('.', glyphs.get(0, 0))
    }

    @Test
    fun `out of bounds writes are silently dropped`() {
        val glyphs = grid()

        glyphs.put(-1, 0, 'X')
        glyphs.put(size.width, 0, 'X')
        glyphs.set(0, -1, 'X')
        glyphs.set(0, size.height, 'X')

        for (y in 0 until size.height) {
            for (x in 0 until size.width) assertEquals(' ', glyphs.get(x, y))
        }
    }

    @Test
    fun `in bounds writes reject characters that are not one terminal cell`() {
        val glyphs = grid()

        assertThrows<IllegalArgumentException> { glyphs.put(0, 0, '\uD83D') }
        assertThrows<IllegalArgumentException> { glyphs.put(0, 0, '中') }
        assertThrows<IllegalArgumentException> { glyphs.set(0, 0, '\u0001') }
    }

    @Test
    fun `draw paints styled spaces and clips to the destination`() {
        val glyphs = grid()
        glyphs.put(1, 0, 'X')
        val destination = ScreenBuffer(2, 1)

        glyphs.draw(tenter.screen.Canvas.of(destination))

        assertEquals(Cell(" ", style(' ')), destination.get(0, 0))
        assertEquals(Cell("X", style('X')), destination.get(1, 0))
        assertFalse(destination.get(0, 0).style == Cell.Style.DEFAULT)
    }

    @Test
    fun `grid itself is a view with its intrinsic dimensions`() {
        val glyphs = grid()
        glyphs.put(2, 1, 'X')

        val buffer = render(glyphs, glyphs.width, glyphs.height)

        assertEquals("X", buffer.get(2, 1).char)
        assertEquals(size.width, glyphs.width)
        assertEquals(size.height, glyphs.height)
    }
}
