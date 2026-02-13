package battletech.tui.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ScreenBufferTest {

    @Test
    fun `new buffer has all cells set to default`() {
        val buffer = ScreenBuffer(3, 2)

        val defaultCell = Cell(' ', Color.DEFAULT, Color.DEFAULT)
        for (x in 0 until 3) {
            for (y in 0 until 2) {
                assertEquals(defaultCell, buffer.get(x, y))
            }
        }
    }

    @Test
    fun `set and get cell`() {
        val buffer = ScreenBuffer(5, 5)
        val cell = Cell('A', Color.RED, Color.BLUE)

        buffer.set(2, 3, cell)

        assertEquals(cell, buffer.get(2, 3))
    }

    @Test
    fun `set out of bounds is ignored`() {
        val buffer = ScreenBuffer(3, 3)

        buffer.set(-1, 0, Cell('X'))
        buffer.set(0, -1, Cell('X'))
        buffer.set(3, 0, Cell('X'))
        buffer.set(0, 3, Cell('X'))

        assertEquals(Cell(' '), buffer.get(0, 0))
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

        assertEquals(Cell('H', Color.GREEN, Color.BLACK), buffer.get(2, 0))
        assertEquals(Cell('i', Color.GREEN, Color.BLACK), buffer.get(3, 0))
        assertEquals(Cell(' '), buffer.get(4, 0))
    }

    @Test
    fun `writeString truncates at buffer edge`() {
        val buffer = ScreenBuffer(5, 1)

        buffer.writeString(3, 0, "Hello")

        assertEquals(Cell('H'), buffer.get(3, 0))
        assertEquals(Cell('e'), buffer.get(4, 0))
    }

    @Test
    fun `writeString uses default colors when not specified`() {
        val buffer = ScreenBuffer(5, 1)

        buffer.writeString(0, 0, "AB")

        assertEquals(Cell('A', Color.DEFAULT, Color.DEFAULT), buffer.get(0, 0))
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
        b.set(1, 2, Cell('X', Color.RED, Color.DEFAULT))

        val changes = a.diff(b)

        assertEquals(1, changes.size)
        assertEquals(CellChange(1, 2, Cell('X', Color.RED, Color.DEFAULT)), changes[0])
    }

    @Test
    fun `diff returns multiple changes`() {
        val a = ScreenBuffer(3, 3)
        val b = ScreenBuffer(3, 3)
        b.set(0, 0, Cell('A'))
        b.set(2, 2, Cell('B'))

        val changes = a.diff(b)

        assertEquals(2, changes.size)
    }

    @Test
    fun `width and height are accessible`() {
        val buffer = ScreenBuffer(10, 20)

        assertEquals(10, buffer.width)
        assertEquals(20, buffer.height)
    }
}
