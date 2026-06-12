package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tui.game.PanelScroll
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class MouseInputTest {

    @Test
    fun `left click at hex origin maps to hex 0,0`() {
        val event = MouseEvent(x = 5, y = 3, left = true)

        val result = InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)

        assertEquals(HexCoordinates(0, 0), result)
    }

    @Test
    fun `left click at hex 1,0 position maps correctly`() {
        val event = MouseEvent(x = 13, y = 5, left = true)

        val result = InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)

        assertEquals(HexCoordinates(1, 0), result)
    }

    @Test
    fun `left click at hex 2,1 position maps correctly`() {
        val event = MouseEvent(x = 21, y = 7, left = true)

        val result = InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)

        assertEquals(HexCoordinates(2, 1), result)
    }

    @Test
    fun `release event returns null`() {
        val event = MouseEvent(x = 5, y = 3)

        assertNull(InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2))
    }

    @Test
    fun `right click returns null`() {
        val event = MouseEvent(x = 5, y = 3, right = true)

        assertNull(InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2))
    }

    @Test
    fun `click in board margin returns null`() {
        val event = MouseEvent(x = 1, y = 1, left = true)

        assertNull(InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2))
    }

    @Test
    fun `wheelUp over panel returns negative delta`() {
        val event = MouseEvent(x = 10, y = 10, wheelUp = true)

        assertEquals(-PanelScroll.STEP, InputMapper.scrollDelta(event, overPanel = true))
    }

    @Test
    fun `wheelUp not over panel returns negative delta`() {
        val event = MouseEvent(x = 10, y = 10, wheelUp = true)

        assertEquals(-PanelScroll.STEP, InputMapper.scrollDelta(event, overPanel = false))
    }

    @Test
    fun `wheelDown over panel returns positive delta`() {
        val event = MouseEvent(x = 10, y = 10, wheelDown = true)

        assertEquals(PanelScroll.STEP, InputMapper.scrollDelta(event, overPanel = true))
    }

    @Test
    fun `wheelDown not over panel returns positive delta`() {
        val event = MouseEvent(x = 10, y = 10, wheelDown = true)

        assertEquals(PanelScroll.STEP, InputMapper.scrollDelta(event, overPanel = false))
    }

    @Test
    fun `left press over panel returns negative delta (Mordant wheel workaround)`() {
        val event = MouseEvent(x = 10, y = 10, left = true)

        assertEquals(-PanelScroll.STEP, InputMapper.scrollDelta(event, overPanel = true))
    }

    @Test
    fun `right press over panel returns positive delta (Mordant wheel workaround)`() {
        val event = MouseEvent(x = 10, y = 10, right = true)

        assertEquals(PanelScroll.STEP, InputMapper.scrollDelta(event, overPanel = true))
    }

    @Test
    fun `left press not over panel returns null`() {
        val event = MouseEvent(x = 10, y = 10, left = true)

        assertNull(InputMapper.scrollDelta(event, overPanel = false))
    }

    @Test
    fun `right press not over panel returns null`() {
        val event = MouseEvent(x = 10, y = 10, right = true)

        assertNull(InputMapper.scrollDelta(event, overPanel = false))
    }

    @Test
    fun `release event over panel returns null`() {
        val event = MouseEvent(x = 10, y = 10)

        assertNull(InputMapper.scrollDelta(event, overPanel = true))
    }

    @Test
    fun `wheelUp takes precedence over overPanel=false`() {
        val event = MouseEvent(x = 10, y = 10, wheelUp = true)

        assertEquals(-PanelScroll.STEP, InputMapper.scrollDelta(event, overPanel = false))
    }
}
