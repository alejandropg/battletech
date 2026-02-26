package battletech.tui.input

import battletech.tactical.model.HexDirection
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class InputMapperTest {

    @Test
    fun `arrow up maps to move cursor north`() {
        val event = KeyboardEvent("ArrowUp", ctrl = false, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertEquals(InputAction.MoveCursor(HexDirection.N), action)
    }

    @Test
    fun `arrow down maps to move cursor south`() {
        val event = KeyboardEvent("ArrowDown", ctrl = false, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertEquals(InputAction.MoveCursor(HexDirection.S), action)
    }

    @Test
    fun `arrow right maps to move cursor southeast`() {
        val event = KeyboardEvent("ArrowRight", ctrl = false, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertEquals(InputAction.MoveCursor(HexDirection.SE), action)
    }

    @Test
    fun `arrow left maps to move cursor northwest`() {
        val event = KeyboardEvent("ArrowLeft", ctrl = false, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertEquals(InputAction.MoveCursor(HexDirection.NW), action)
    }

    @Test
    fun `enter maps to confirm`() {
        val event = KeyboardEvent("Enter", ctrl = false, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertEquals(InputAction.Confirm, action)
    }

    @Test
    fun `escape maps to cancel`() {
        val event = KeyboardEvent("Escape", ctrl = false, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertEquals(InputAction.Cancel, action)
    }

    @Test
    fun `q maps to quit`() {
        val event = KeyboardEvent("q", ctrl = false, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertEquals(InputAction.Quit, action)
    }

    @Test
    fun `tab maps to cycle unit`() {
        val event = KeyboardEvent("Tab", ctrl = false, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertEquals(InputAction.CycleUnit, action)
    }

    @Test
    fun `number keys map to select action`() {
        assertEquals(
            InputAction.SelectAction(1),
            InputMapper.mapKeyboardEvent(KeyboardEvent("1", ctrl = false, alt = false))
        )
        assertEquals(
            InputAction.SelectAction(2),
            InputMapper.mapKeyboardEvent(KeyboardEvent("2", ctrl = false, alt = false))
        )
        assertEquals(
            InputAction.SelectAction(9),
            InputMapper.mapKeyboardEvent(KeyboardEvent("9", ctrl = false, alt = false))
        )
    }

    @Test
    fun `unknown key returns null`() {
        val event = KeyboardEvent("F12", ctrl = false, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertNull(action)
    }

    @Test
    fun `ctrl+c maps to quit`() {
        val event = KeyboardEvent("c", ctrl = true, alt = false)

        val action = InputMapper.mapKeyboardEvent(event)

        assertEquals(InputAction.Quit, action)
    }

    @Test
    fun `mouse left click maps to click hex`() {
        val event = MouseEvent(x = 10, y = 5, left = true)

        val action = InputMapper.mapMouseEvent(event, boardX = 0, boardY = 0)

        assertEquals(InputAction.ClickHex((action as InputAction.ClickHex).coords), action)
    }
}
