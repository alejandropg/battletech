package tenter.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.ScreenBuffer
import tenter.view.TextCursor

internal class PipTrackTest {

    private val used = Cell.Style(ChromeRole.DANGER)
    private val empty = Cell.Style(ChromeRole.TEXT_PRIMARY)

    private fun content(width: Int = 40, height: Int = 10): Pair<TextCursor, ScreenBuffer> {
        val buffer = ScreenBuffer(width, height)
        return TextCursor(Canvas.of(buffer)) to buffer
    }

    @Test
    fun `draws used pips filled and the remainder empty`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 20)
        val (content, buffer) = content()

        track.draw(content, column = 0, row = 0, used = 3, capacity = 5, usedStyle = used, emptyStyle = empty)

        val row = (0 until 9).joinToString("") { buffer.get(it, 0).char }
        assertEquals("F F F E E", row)
    }

    @Test
    fun `styles filled and empty pips independently`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 20)
        val (content, buffer) = content()

        track.draw(content, column = 0, row = 0, used = 1, capacity = 2, usedStyle = used, emptyStyle = empty)

        assertEquals(ChromeRole.DANGER, buffer.get(0, 0).style.fg)
        assertEquals(ChromeRole.TEXT_PRIMARY, buffer.get(2, 0).style.fg)
    }

    @Test
    fun `wraps to a new row every perRow pips`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 3)
        val (content, buffer) = content()

        track.draw(content, column = 0, row = 0, used = 5, capacity = 7, usedStyle = used, emptyStyle = empty)

        val row0 = (0 until 5).joinToString("") { buffer.get(it, 0).char }
        val row1 = (0 until 5).joinToString("") { buffer.get(it, 1).char }
        val row2 = (0 until 5).joinToString("") { buffer.get(it, 2).char }
        assertEquals("F F F", row0)
        assertEquals("F F E", row1)
        assertEquals("E", row2.trim())
    }

    @Test
    fun `clamps used above capacity`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 20)
        val (content, buffer) = content()

        track.draw(content, column = 0, row = 0, used = 99, capacity = 3, usedStyle = used, emptyStyle = empty)

        val row = (0 until 5).joinToString("") { buffer.get(it, 0).char }
        assertEquals("F F F", row)
    }

    @Test
    fun `returns rows consumed without moving the cursor`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 3)
        val (content, _) = content()

        val rows = track.draw(content, column = 0, row = 0, used = 5, capacity = 7, usedStyle = used, emptyStyle = empty)

        assertEquals(3, rows)
        assertEquals(0, content.row)
    }

    @Test
    fun `rows reports the row count for a capacity without drawing`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 3)

        assertEquals(3, track.rows(7))
        assertEquals(1, track.rows(3))
        assertEquals(1, track.rows(0))
    }

    @Test
    fun `draws at the requested row, not the cursor's row`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 20)
        val (content, buffer) = content()
        content.newLine()
        content.newLine()

        track.draw(content, column = 0, row = 2, used = 1, capacity = 1, usedStyle = used, emptyStyle = empty)

        assertEquals("F", buffer.get(0, 2).char)
        assertEquals(" ", buffer.get(0, 0).char)
    }

    @Test
    fun `offsets pips by the requested column`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 20)
        val (content, buffer) = content()

        track.draw(content, column = 5, row = 0, used = 1, capacity = 1, usedStyle = used, emptyStyle = empty)

        assertEquals("F", buffer.get(5, 0).char)
        assertEquals(" ", buffer.get(0, 0).char)
    }

    @Test
    fun `places two tracks side by side on the same row`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 20)
        val (content, buffer) = content()

        track.draw(content, column = 0, row = 0, used = 1, capacity = 1, usedStyle = used, emptyStyle = empty)
        track.draw(content, column = 4, row = 0, used = 1, capacity = 1, usedStyle = used, emptyStyle = empty)

        val row0 = (0 until 5).joinToString("") { buffer.get(it, 0).char }
        assertEquals("F   F", row0)
    }

    @Test
    fun `rejects a non-positive perRow`() {
        assertThrows(IllegalArgumentException::class.java) {
            PipTrack(filled = "F", empty = "E", perRow = 0)
        }
    }

    @Test
    fun `drawAdvancing draws at the cursor's row and moves it past the rows used`() {
        val track = PipTrack(filled = "F", empty = "E", perRow = 3)
        val (content, buffer) = content()
        content.newLine()

        track.drawAdvancing(content, column = 0, used = 5, capacity = 7, usedStyle = used, emptyStyle = empty)

        assertEquals("F F F", (0 until 5).joinToString("") { buffer.get(it, 1).char })
        assertEquals("F F E", (0 until 5).joinToString("") { buffer.get(it, 2).char })
        assertEquals(4, content.row)
    }
}
