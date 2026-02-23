package battletech.tui.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ScreenBufferTest {

    @Test
    fun `new buffer has all cells set to default`() {
        val buffer = ScreenBuffer(3, 2)

        val defaultCell = Cell(" ", Color.DEFAULT, Color.DEFAULT)
        for (x in 0 until 3) {
            for (y in 0 until 2) {
                assertEquals(defaultCell, buffer.get(x, y))
            }
        }
    }

    @Test
    fun `set and get cell`() {
        val buffer = ScreenBuffer(5, 5)
        val cell = Cell("A", Color.RED, Color.BLUE)

        buffer.set(2, 3, cell)

        assertEquals(cell, buffer.get(2, 3))
    }

    @Test
    fun `set out of bounds is ignored`() {
        val buffer = ScreenBuffer(3, 3)

        buffer.set(-1, 0, Cell("X"))
        buffer.set(0, -1, Cell("X"))
        buffer.set(3, 0, Cell("X"))
        buffer.set(0, 3, Cell("X"))

        assertEquals(Cell(" "), buffer.get(0, 0))
    }

    @Test
    fun `get out of bounds throws`() {
        val buffer = ScreenBuffer(3, 3)

        assertThrows<IndexOutOfBoundsException> { buffer.get(-1, 0) }
        assertThrows<IndexOutOfBoundsException> { buffer.get(3, 0) }
        assertThrows<IndexOutOfBoundsException> { buffer.get(0, -1) }
        assertThrows<IndexOutOfBoundsException> { buffer.get(0, 3) }
    }

    @Test
    fun `writeString places characters horizontally`() {
        val buffer = ScreenBuffer(10, 1)

        buffer.writeString(2, 0, "Hi", Color.GREEN, Color.BLACK)

        assertEquals(Cell("H", Color.GREEN, Color.BLACK), buffer.get(2, 0))
        assertEquals(Cell("i", Color.GREEN, Color.BLACK), buffer.get(3, 0))
        assertEquals(Cell(" "), buffer.get(4, 0))
    }

    @Test
    fun `writeString truncates at buffer edge`() {
        val buffer = ScreenBuffer(5, 1)

        buffer.writeString(3, 0, "Hello")

        assertEquals(Cell("H"), buffer.get(3, 0))
        assertEquals(Cell("e"), buffer.get(4, 0))
    }

    @Test
    fun `writeString uses default colors when not specified`() {
        val buffer = ScreenBuffer(5, 1)

        buffer.writeString(0, 0, "AB")

        assertEquals(Cell("A", Color.DEFAULT, Color.DEFAULT), buffer.get(0, 0))
    }

    @Test
    fun `diff returns empty list for identical buffers`() {
        val a = ScreenBuffer(3, 3)
        val b = ScreenBuffer(3, 3)

        val changes = a.diff(b)

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `diff returns changed cells`() {
        val a = ScreenBuffer(3, 3)
        val b = ScreenBuffer(3, 3)
        b.set(1, 2, Cell("X", Color.RED, Color.DEFAULT))

        val changes = a.diff(b)

        assertEquals(1, changes.size)
        assertEquals(CellChange(1, 2, Cell("X", Color.RED, Color.DEFAULT)), changes[0])
    }

    @Test
    fun `diff returns multiple changes`() {
        val a = ScreenBuffer(3, 3)
        val b = ScreenBuffer(3, 3)
        b.set(0, 0, Cell("A"))
        b.set(2, 2, Cell("B"))

        val changes = a.diff(b)

        assertEquals(2, changes.size)
    }

    @Test
    fun `width and height are accessible`() {
        val buffer = ScreenBuffer(10, 20)

        assertEquals(10, buffer.width)
        assertEquals(20, buffer.height)
    }

    @Test
    fun `drawBox renders corners`() {
        val buffer = ScreenBuffer(10, 5)

        buffer.drawBox(0, 0, 10, 5)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(9, 0).char)
        assertEquals("╰", buffer.get(0, 4).char)
        assertEquals("╯", buffer.get(9, 4).char)
    }

    @Test
    fun `drawBox renders horizontal and vertical borders`() {
        val buffer = ScreenBuffer(6, 4)

        buffer.drawBox(0, 0, 6, 4)

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
    fun `drawBox renders title`() {
        val buffer = ScreenBuffer(20, 3)

        buffer.drawBox(0, 0, 20, 3, "TEST")

        assertEquals(" ", buffer.get(3, 0).char)
        assertEquals("T", buffer.get(4, 0).char)
        assertEquals("E", buffer.get(5, 0).char)
        assertEquals("S", buffer.get(6, 0).char)
        assertEquals("T", buffer.get(7, 0).char)
        assertEquals(" ", buffer.get(8, 0).char)
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(4, 0).fg)
        assertEquals(Color.GREEN, buffer.get(0, 0).fg)
    }

    @Test
    fun `drawBox uses specified colors`() {
        val buffer = ScreenBuffer(10, 3)

        buffer.drawBox(0, 0, 10, 3, "", Color.RED, Color.WHITE)

        assertEquals(Color.RED, buffer.get(0, 0).fg)
    }

    @Test
    fun `drawBox skips title when box too narrow`() {
        val buffer = ScreenBuffer(8, 3)

        buffer.drawBox(0, 0, 8, 3, "TOOLONG")

        assertEquals("─", buffer.get(3, 0).char)
    }

    @Test
    fun `drawBox with offset position`() {
        val buffer = ScreenBuffer(15, 8)

        buffer.drawBox(2, 1, 10, 5)

        assertEquals("╭", buffer.get(2, 1).char)
        assertEquals("╯", buffer.get(11, 5).char)
    }
}
