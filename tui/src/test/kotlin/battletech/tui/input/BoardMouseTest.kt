package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Point
import battletech.tui.view.BoardView

internal class BoardMouseTest {

    @Test
    fun `left click maps to hex coordinates`() {
        val event = MouseEvent(x = 5, y = 3, left = true)

        val result = BoardMouse.mapContentToHex(event, Point(BoardView.MAP_ORIGIN_X, BoardView.MAP_ORIGIN_Y))

        assertEquals(HexCoordinates(0, 0), result)
    }

    @Test
    fun `non-left click returns null`() {
        val event = MouseEvent(x = 5, y = 3)

        assertNull(BoardMouse.mapContentToHex(event, Point(BoardView.MAP_ORIGIN_X - 1, BoardView.MAP_ORIGIN_Y - 1)))
    }

    @Test
    fun `right click returns null`() {
        val event = MouseEvent(x = 5, y = 3, right = true)

        assertNull(BoardMouse.mapContentToHex(event, Point(BoardView.MAP_ORIGIN_X - 1, BoardView.MAP_ORIGIN_Y - 1)))
    }

    @Test
    fun `click in margin returns null`() {
        val event = MouseEvent(x = 1, y = 1, left = true)

        assertNull(BoardMouse.mapContentToHex(event, Point(BoardView.MAP_ORIGIN_X - 1, BoardView.MAP_ORIGIN_Y - 1)))
    }

    @Test
    fun `left click at hex 1,0 maps correctly`() {
        val event = MouseEvent(x = 13, y = 5, left = true)

        val result = BoardMouse.mapContentToHex(event, Point(BoardView.MAP_ORIGIN_X + 8, BoardView.MAP_ORIGIN_Y + 2))

        assertEquals(HexCoordinates(1, 0), result)
    }

    @Test
    fun `left click at hex 2,1 maps correctly`() {
        val event = MouseEvent(x = 21, y = 7, left = true)

        val result = BoardMouse.mapContentToHex(event, Point(BoardView.MAP_ORIGIN_X + 16, BoardView.MAP_ORIGIN_Y + 4))

        assertEquals(HexCoordinates(2, 1), result)
    }

    @Test
    fun `scroll shifts which hex a click resolves to`() {
        // Without scroll this is hex (0,0) (see the first test above); scrolled one hex
        // stride right and down, the same screen click now resolves against content
        // shifted by (7,4), landing on hex (1,0).
        val event = MouseEvent(x = 5, y = 3, left = true)

        val result = BoardMouse.mapContentToHex(
            event,
            Point(BoardView.MAP_ORIGIN_X + 7, BoardView.MAP_ORIGIN_Y + 4),
        )

        assertEquals(HexCoordinates(1, 0), result)
    }
}
