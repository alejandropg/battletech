package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class MouseInputTest {

    @Test
    fun `left click at hex origin maps to hex 0,0`() {
        val event = MouseEvent(x = 5, y = 3, left = true)
        val action = InputMapper.mapMouseEvent(event, boardX = 2, boardY = 2)

        assertEquals(InputAction.ClickHex(HexCoordinates(0, 0)), action)
    }

    @Test
    fun `left click at hex 1,0 position maps correctly`() {
        // Hex (1,0): charX=8, charY=2 (odd col offset) -> event coords = charX+2, charY+2
        val event = MouseEvent(x = 13, y = 5, left = true)
        val action = InputMapper.mapMouseEvent(event, boardX = 2, boardY = 2)

        assertEquals(InputAction.ClickHex(HexCoordinates(1, 0)), action)
    }

    @Test
    fun `left click at hex 2,1 position maps correctly`() {
        // Hex (2,1): charX=16, charY=4, content around (19, 5) -> event coords = 19+2, 5+2
        val event = MouseEvent(x = 21, y = 7, left = true)
        val action = InputMapper.mapMouseEvent(event, boardX = 2, boardY = 2)

        assertEquals(InputAction.ClickHex(HexCoordinates(2, 1)), action)
    }

    @Test
    fun `release event returns null`() {
        val event = MouseEvent(x = 5, y = 3)
        val action = InputMapper.mapMouseEvent(event, boardX = 2, boardY = 2)

        assertNull(action)
    }

    @Test
    fun `right click returns null`() {
        val event = MouseEvent(x = 5, y = 3, right = true)
        val action = InputMapper.mapMouseEvent(event, boardX = 2, boardY = 2)

        assertNull(action)
    }

    @Test
    fun `click in board margin returns null`() {
        val event = MouseEvent(x = 1, y = 1, left = true)
        val action = InputMapper.mapMouseEvent(event, boardX = 2, boardY = 2)

        assertNull(action)
    }
}
