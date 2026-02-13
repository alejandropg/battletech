package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MouseInputTest {

    @Test
    fun `click at hex origin maps to hex 0,0`() {
        val action = InputMapper.mapMouseEvent(x = 3, y = 1, button = 0, scrollX = 0, scrollY = 0)

        assertEquals(InputAction.ClickHex(HexCoordinates(0, 0)), action)
    }

    @Test
    fun `click at hex 1,0 position maps correctly`() {
        // Hex (1,0): charX=8, charY=2 (odd col offset)
        val action = InputMapper.mapMouseEvent(x = 11, y = 3, button = 0, scrollX = 0, scrollY = 0)

        assertEquals(InputAction.ClickHex(HexCoordinates(1, 0)), action)
    }

    @Test
    fun `click with scroll offset maps to correct hex`() {
        // Click at (3, 1) with scroll of (8, 0) should map to hex (1, 0)
        val action = InputMapper.mapMouseEvent(x = 3, y = 1, button = 0, scrollX = 8, scrollY = 0)

        assertEquals(InputAction.ClickHex(HexCoordinates(1, 0)), action)
    }

    @Test
    fun `click at hex 2,1 position maps correctly`() {
        // Hex (2,1): charX=16, charY=4, content around (19, 5)
        val action = InputMapper.mapMouseEvent(x = 19, y = 5, button = 0, scrollX = 0, scrollY = 0)

        assertEquals(InputAction.ClickHex(HexCoordinates(2, 1)), action)
    }
}
