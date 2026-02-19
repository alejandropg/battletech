package battletech.tui.input

import battletech.tactical.model.HexDirection
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class InputMapperTest {

    @Test
    fun `arrow up maps to move cursor north`() {
        val action = InputMapper.mapKeyboardEvent("ArrowUp", ctrl = false, alt = false)

        assertEquals(InputAction.MoveCursor(HexDirection.N), action)
    }

    @Test
    fun `arrow down maps to move cursor south`() {
        val action = InputMapper.mapKeyboardEvent("ArrowDown", ctrl = false, alt = false)

        assertEquals(InputAction.MoveCursor(HexDirection.S), action)
    }

    @Test
    fun `arrow right maps to move cursor southeast`() {
        val action = InputMapper.mapKeyboardEvent("ArrowRight", ctrl = false, alt = false)

        assertEquals(InputAction.MoveCursor(HexDirection.SE), action)
    }

    @Test
    fun `arrow left maps to move cursor northwest`() {
        val action = InputMapper.mapKeyboardEvent("ArrowLeft", ctrl = false, alt = false)

        assertEquals(InputAction.MoveCursor(HexDirection.NW), action)
    }

    @Test
    fun `enter maps to confirm`() {
        val action = InputMapper.mapKeyboardEvent("Enter", ctrl = false, alt = false)

        assertEquals(InputAction.Confirm, action)
    }

    @Test
    fun `escape maps to cancel`() {
        val action = InputMapper.mapKeyboardEvent("Escape", ctrl = false, alt = false)

        assertEquals(InputAction.Cancel, action)
    }

    @Test
    fun `q maps to quit`() {
        val action = InputMapper.mapKeyboardEvent("q", ctrl = false, alt = false)

        assertEquals(InputAction.Quit, action)
    }

    @Test
    fun `tab maps to cycle unit`() {
        val action = InputMapper.mapKeyboardEvent("Tab", ctrl = false, alt = false)

        assertEquals(InputAction.CycleUnit, action)
    }

    @Test
    fun `number keys map to select action`() {
        assertEquals(InputAction.SelectAction(1), InputMapper.mapKeyboardEvent("1", ctrl = false, alt = false))
        assertEquals(InputAction.SelectAction(2), InputMapper.mapKeyboardEvent("2", ctrl = false, alt = false))
        assertEquals(InputAction.SelectAction(9), InputMapper.mapKeyboardEvent("9", ctrl = false, alt = false))
    }

    @Test
    fun `unknown key returns null`() {
        val action = InputMapper.mapKeyboardEvent("F12", ctrl = false, alt = false)

        assertNull(action)
    }

    @Test
    fun `ctrl+c maps to quit`() {
        val action = InputMapper.mapKeyboardEvent("c", ctrl = true, alt = false)

        assertEquals(InputAction.Quit, action)
    }

    @Test
    fun `mouse left click maps to click hex`() {
        val event = MouseEvent(x = 10, y = 5, left = true)
        val action = InputMapper.mapMouseEvent(event, boardX = 0, boardY = 0)

        assertEquals(InputAction.ClickHex((action as InputAction.ClickHex).coords), action)
    }
}
