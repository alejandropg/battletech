package battletech.tui.hex

import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.model.HexDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UnitRendererTest {

    @Test
    fun `renders unit initial at hex center`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.N, Color.CYAN)

        assertEquals('A', buffer.get(4, 3).char)
        assertEquals(Color.CYAN, buffer.get(4, 3).fg)
    }

    @Test
    fun `renders north facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.N, Color.CYAN)

        assertEquals('^', buffer.get(4, 2).char)
    }

    @Test
    fun `renders south facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.S, Color.CYAN)

        assertEquals('v', buffer.get(4, 2).char)
    }

    @Test
    fun `renders northeast facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.NE, Color.CYAN)

        assertEquals('/', buffer.get(5, 2).char)
    }

    @Test
    fun `renders southeast facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.SE, Color.CYAN)

        assertEquals('\\', buffer.get(5, 2).char)
    }

    @Test
    fun `renders southwest facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.SW, Color.CYAN)

        assertEquals('/', buffer.get(3, 2).char)
    }

    @Test
    fun `renders northwest facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.NW, Color.CYAN)

        assertEquals('\\', buffer.get(3, 2).char)
    }
}
