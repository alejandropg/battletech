package tenter.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class CanvasTest {

    @Test
    fun `get and set within bounds`() {
        val canvas = Canvas.offscreen(5, 5)
        val cell = Cell("A", Cell.Style(ChromeRole.DANGER, ChromeRole.INFO))

        canvas.set(2, 3, cell)

        assertEquals(cell, canvas.get(2, 3))
    }

    @Test
    fun `set out of bounds is ignored`() {
        val canvas = Canvas.offscreen(3, 3)

        canvas.set(-1, 0, Cell("X"))
        canvas.set(0, -1, Cell("X"))
        canvas.set(3, 0, Cell("X"))
        canvas.set(0, 3, Cell("X"))

        assertEquals(Cell(" "), canvas.get(0, 0))
    }

    @Test
    fun `get out of bounds throws`() {
        val canvas = Canvas.offscreen(3, 3)

        assertThrows<IndexOutOfBoundsException> { canvas.get(-1, 0) }
        assertThrows<IndexOutOfBoundsException> { canvas.get(3, 0) }
        assertThrows<IndexOutOfBoundsException> { canvas.get(0, -1) }
        assertThrows<IndexOutOfBoundsException> { canvas.get(0, 3) }
    }

    @Test
    fun `writeString places characters horizontally`() {
        val canvas = Canvas.offscreen(10, 1)

        canvas.writeString(2, 0, "Hi", Cell.Style(ChromeRole.SUCCESS, ChromeRole.TEXT_MUTED))

        assertEquals(Cell("H", Cell.Style(ChromeRole.SUCCESS, ChromeRole.TEXT_MUTED)), canvas.get(2, 0))
        assertEquals(Cell("i", Cell.Style(ChromeRole.SUCCESS, ChromeRole.TEXT_MUTED)), canvas.get(3, 0))
        assertEquals(Cell(" "), canvas.get(4, 0))
    }

    @Test
    fun `writeString truncates at canvas edge, not the underlying buffer edge`() {
        val buffer = ScreenBuffer(20, 1)
        val canvas = Canvas.of(buffer).region(3, 0, 5, 1)

        canvas.writeString(0, 0, "Hello world")

        assertEquals("Hello", (0 until 5).joinToString("") { canvas.get(it, 0).char })
        // the sixth character must not have leaked past the canvas into buffer column 8
        assertEquals(Cell(" "), buffer.get(8, 0))
    }

    @Test
    fun `writeString at a negative x drops leading cells and lands the tail at column 0`() {
        val canvas = Canvas.offscreen(5, 1)

        canvas.writeString(-2, 0, "ABCDE")

        assertEquals("C", canvas.get(0, 0).char)
        assertEquals("D", canvas.get(1, 0).char)
        assertEquals("E", canvas.get(2, 0).char)
    }

    @Test
    fun `writeString uses default colors when not specified`() {
        val canvas = Canvas.offscreen(5, 1)

        canvas.writeString(0, 0, "AB")

        assertEquals(Cell("A", Cell.Style(ChromeRole.DEFAULT, ChromeRole.DEFAULT)), canvas.get(0, 0))
    }

    @Test
    fun `writeString puts a non-BMP nerd-font icon in a single cell`() {
        val canvas = Canvas.offscreen(10, 1)
        val die = String(Character.toChars(0xF01CA)) // nf-md-dice_1, 2 UTF-16 chars

        canvas.writeString(2, 0, "A" + die + "B")

        assertEquals("A", canvas.get(2, 0).char)
        assertEquals(die, canvas.get(3, 0).char)
        assertEquals(2, canvas.get(3, 0).char.length) // surrogate pair preserved as one cell
        assertEquals("B", canvas.get(4, 0).char)
        assertEquals(" ", canvas.get(5, 0).char)
    }

    @Test
    fun `writeString reserves two cells for an East-Asian wide codepoint`() {
        val canvas = Canvas.offscreen(10, 1)

        canvas.writeString(0, 0, "中A")

        assertEquals("中", canvas.get(0, 0).char)
        assertEquals("", canvas.get(1, 0).char) // filler reserves the second visual cell
        assertEquals("A", canvas.get(2, 0).char)
    }

    @Test
    fun `writeString skips zero-width combining marks`() {
        val canvas = Canvas.offscreen(5, 1)

        // 'e' + combining acute (U+0301) + 'x'
        canvas.writeString(0, 0, "e\u0301x")

        assertEquals("e", canvas.get(0, 0).char)
        assertEquals("x", canvas.get(1, 0).char)
        assertEquals(" ", canvas.get(2, 0).char)
    }

    @Test
    fun `width and height are accessible`() {
        val canvas = Canvas.offscreen(10, 20)

        assertEquals(10, canvas.width)
        assertEquals(20, canvas.height)
    }

    @Test
    fun `region derives a canvas at a local offset`() {
        val buffer = ScreenBuffer(15, 8)
        val canvas = Canvas.of(buffer).region(2, 1, 10, 5)

        canvas.set(0, 0, Cell("X"))

        assertEquals(Cell("X"), buffer.get(2, 1))
    }

    @Test
    fun `region clamps to the parent rather than escaping it`() {
        val canvas = Canvas.offscreen(10, 10)

        val child = canvas.region(8, 8, 100, 100)

        assertEquals(2, child.width)
        assertEquals(2, child.height)
    }

    @Test
    fun `inset composes across nesting and clamps to zero rather than negative`() {
        val canvas = Canvas.offscreen(10, 10)

        val once = canvas.inset(Insets(2, 2, 2, 2))
        assertEquals(6, once.width)
        assertEquals(6, once.height)

        val twice = once.inset(Insets(2, 2, 2, 2))
        assertEquals(2, twice.width)
        assertEquals(2, twice.height)

        val overInset = twice.inset(Insets.all(5))
        assertEquals(0, overInset.width)
        assertEquals(0, overInset.height)
    }

    @Test
    fun `a canvas cannot write past its own edge into a sibling region`() {
        val buffer = ScreenBuffer(10, 1)
        val left = Canvas.of(buffer).region(0, 0, 5, 1)
        val right = Canvas.of(buffer).region(5, 0, 5, 1)

        left.writeString(0, 0, "AAAAAAAAAA") // ten chars into a five-wide canvas

        assertEquals(Cell(" "), right.get(0, 0))
        assertEquals(Cell(" "), buffer.get(5, 0))
    }

    @Test
    fun `setFg preserves the existing background`() {
        val canvas = Canvas.offscreen(5, 1)
        canvas.set(0, 0, Cell(" ", Cell.Style(ChromeRole.DEFAULT, ChromeRole.INFO)))

        canvas.setFg(0, 0, "X", ChromeRole.DANGER)

        assertEquals(Cell("X", Cell.Style(ChromeRole.DANGER, ChromeRole.INFO)), canvas.get(0, 0))
    }

    @Test
    fun `setFg outside the canvas is a silent no-op, never throws`() {
        val canvas = Canvas.offscreen(3, 3)

        canvas.setFg(-1, 0, "X", ChromeRole.DANGER)
        canvas.setFg(3, 0, "X", ChromeRole.DANGER)

        assertEquals(Cell(" "), canvas.get(0, 0))
    }

    @Test
    fun `blit copies cell char fg and bg from source to destination`() {
        val src = Canvas.offscreen(5, 5)
        src.set(1, 2, Cell("X", Cell.Style(ChromeRole.DANGER, ChromeRole.INFO)))
        val dest = Canvas.offscreen(10, 10)

        dest.blit(src, 1, 2, 3, 4, 1, 1)

        assertEquals(Cell("X", Cell.Style(ChromeRole.DANGER, ChromeRole.INFO)), dest.get(3, 4))
    }

    @Test
    fun `blit clips at destination right and bottom edges`() {
        val src = Canvas.offscreen(5, 5)
        src.set(0, 0, Cell("A", Cell.Style(ChromeRole.SUCCESS)))
        src.set(1, 0, Cell("B", Cell.Style(ChromeRole.SUCCESS)))
        src.set(2, 0, Cell("C", Cell.Style(ChromeRole.SUCCESS)))
        val dest = Canvas.offscreen(4, 4)

        dest.blit(src, 0, 0, 3, 0, 3, 1)

        assertEquals(Cell("A", Cell.Style(ChromeRole.SUCCESS)), dest.get(3, 0))
        assertEquals(Cell(" "), dest.get(2, 0))
    }

    @Test
    fun `blit skips source rows and cols beyond source bounds`() {
        val src = Canvas.offscreen(2, 2)
        src.set(0, 0, Cell("Z", Cell.Style(ChromeRole.ACCENT)))
        val dest = Canvas.offscreen(10, 10)

        dest.blit(src, 0, 0, 0, 0, 5, 5)

        assertEquals(Cell("Z", Cell.Style(ChromeRole.ACCENT)), dest.get(0, 0))
        assertEquals(Cell(" "), dest.get(2, 0))
        assertEquals(Cell(" "), dest.get(0, 2))
    }

    @Test
    fun `blit into a sub-region never writes past that region's own edge`() {
        val buffer = ScreenBuffer(10, 1)
        val src = Canvas.offscreen(5, 1)
        src.set(0, 0, Cell("A"))
        src.set(1, 0, Cell("B"))
        src.set(2, 0, Cell("C"))
        val dest = Canvas.of(buffer).region(0, 0, 2, 1) // narrower than the 3-cell blit

        dest.blit(src, 0, 0, 0, 0, 3, 1)

        assertEquals(Cell(" "), buffer.get(2, 0), "must not leak past the 2-wide dest canvas")
    }

    @Test
    fun `contentHeight measures the last row holding a glyph or background tint`() {
        val canvas = Canvas.offscreen(5, 10)
        canvas.writeString(0, 3, "hi")

        assertEquals(4, canvas.contentHeight())
    }

    @Test
    fun `contentHeight is zero for a blank canvas`() {
        val canvas = Canvas.offscreen(5, 10)

        assertEquals(0, canvas.contentHeight())
        assertFalse(canvas.get(0, 0).char != " ")
    }
}
