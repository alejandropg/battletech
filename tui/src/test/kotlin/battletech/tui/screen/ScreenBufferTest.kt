package battletech.tui.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ScreenBufferTest {

    @Test
    fun `new buffer has all cells set to default`() {
        val buffer = ScreenBuffer(3, 2)

        val defaultCell = Cell(" ", Cell.Style(Color.DEFAULT, Color.DEFAULT))
        for (x in 0 until 3) {
            for (y in 0 until 2) {
                assertEquals(defaultCell, buffer.get(x, y))
            }
        }
    }

    @Test
    fun `set and get cell`() {
        val buffer = ScreenBuffer(5, 5)
        val cell = Cell("A", Cell.Style(Color.RED, Color.BLUE))

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
    fun `width and height are accessible`() {
        val buffer = ScreenBuffer(10, 20)

        assertEquals(10, buffer.width)
        assertEquals(20, buffer.height)
    }
}
