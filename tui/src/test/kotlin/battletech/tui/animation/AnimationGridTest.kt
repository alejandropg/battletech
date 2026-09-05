package battletech.tui.animation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Cell

internal class AnimationGridTest {

    private val priority: (Char) -> Int = { c -> if (c == 'X') 10 else if (c == '.') 1 else 0 }

    /** 'X' is the only glyph with a style of its own, so [Glyphs.styleAt] is directly observable. */
    private val style: (Char) -> Cell.Style =
        { c -> if (c == 'X') Cell.Style(fg = ANIMATION_DANGER) else Cell.Style(fg = ANIMATION_GRAY) }

    private val size = AnimationSize(width = 70, height = 20)

    private fun glyphs(priority: (Char) -> Int = this.priority) = Glyphs(size, priority, style)

    @Test
    fun `a higher-priority glyph overwrites a lower-priority one`() {
        val glyphs = glyphs()
        glyphs.put(2, 2, '.')
        glyphs.put(2, 2, 'X')
        assertEquals('X', glyphs.get(2, 2))
    }

    @Test
    fun `a lower-priority glyph never overwrites a higher-priority one`() {
        val glyphs = glyphs()
        glyphs.put(2, 2, 'X')
        glyphs.put(2, 2, '.')
        assertEquals('X', glyphs.get(2, 2))
    }

    @Test
    fun `equal priority overwrites (last write wins)`() {
        val samePriority: (Char) -> Int = { 0 }
        val glyphs = glyphs(samePriority)
        glyphs.put(1, 1, 'a')
        glyphs.put(1, 1, 'b')
        assertEquals('b', glyphs.get(1, 1))
    }

    @Test
    fun `put outside the grid is silently dropped`() {
        val glyphs = glyphs()
        glyphs.put(-1, 0, 'X')
        glyphs.put(0, -1, 'X')
        glyphs.put(size.width, 0, 'X')
        glyphs.put(0, size.height, 'X')
        // Nothing thrown, and no in-bounds cell was touched.
        for (y in 0 until size.height) for (x in 0 until size.width) assertEquals(' ', glyphs.get(x, y))
    }

    @Test
    fun `set bypasses priority entirely`() {
        val glyphs = glyphs()
        glyphs.put(0, 0, 'X')
        glyphs.set(0, 0, '.')
        assertEquals('.', glyphs.get(0, 0))
    }

    @Test
    fun `set outside the grid is silently dropped`() {
        val glyphs = glyphs()
        glyphs.set(-1, 0, 'X')
        glyphs.set(size.width, size.height, 'X')
        // No exception — that's the whole assertion.
    }

    @Test
    fun `glyphs use the supplied animation size`() {
        val small = Glyphs(AnimationSize(width = 3, height = 2), priority, style)

        assertEquals(3, small.width)
        assertEquals(2, small.height)
        small.put(2, 1, 'X')
        assertEquals('X', small.get(2, 1))
    }

    @Test
    fun `a frame carries its own palette, so styleAt needs no help from the animation`() {
        val glyphs = glyphs()
        glyphs.put(1, 1, 'X')

        assertEquals(size.width, glyphs.width)
        assertEquals(size.height, glyphs.height)
        assertEquals(Cell.Style(fg = ANIMATION_DANGER), glyphs.styleAt(1, 1))
        assertEquals(Cell.Style(fg = ANIMATION_GRAY), glyphs.styleAt(0, 0), "an untouched cell is a space")
    }

    @Test
    fun `pyRound is half-to-even at exact half boundaries, unlike Kotlin's half-up roundToInt`() {
        assertEquals(2, pyRound(2.5))
        assertEquals(4, pyRound(3.5))
        assertEquals(0, pyRound(0.5))
        assertEquals(-2, pyRound(-2.5))
    }

    @Test
    fun `pointBetween clamps progress to 0 point 0 to 1 point 0`() {
        val origin = point(0, 0)
        val target = point(10, 0)
        assertEquals(0.0, pointBetween(origin, target, -5.0).first)
        assertEquals(10.0, pointBetween(origin, target, 5.0).first)
        assertEquals(5.0, pointBetween(origin, target, 0.5).first)
    }
}
