package battletech.tui.hex

import battletech.tactical.model.HexDirection
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UnitRendererTest {

    private val ICON_FACING_N  = String(Character.toChars(0xF09C7))
    private val ICON_FACING_NE = String(Character.toChars(0xF09C5))
    private val ICON_FACING_SE = String(Character.toChars(0xF09B9))
    private val ICON_FACING_S  = String(Character.toChars(0xF09BF))
    private val ICON_FACING_SW = String(Character.toChars(0xF09B7))
    private val ICON_FACING_NW = String(Character.toChars(0xF09C3))

    @Test
    fun `renders unit initial at hex center`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.N, Color.CYAN)

        assertEquals("A", buffer.get(4, 3).char)
        assertEquals(Color.CYAN, buffer.get(4, 3).fg)
    }

    @Test
    fun `renders north facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.N, Color.CYAN)

        assertEquals(ICON_FACING_N, buffer.get(4, 2).char)
    }

    @Test
    fun `renders south facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.S, Color.CYAN)

        assertEquals(ICON_FACING_S, buffer.get(4, 3).char)
        assertEquals("A", buffer.get(4, 2).char)
    }

    @Test
    fun `renders northeast facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.NE, Color.CYAN)

        assertEquals(ICON_FACING_NE, buffer.get(5, 2).char)
    }

    @Test
    fun `renders southeast facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.SE, Color.CYAN)

        assertEquals(ICON_FACING_SE, buffer.get(5, 3).char)
        assertEquals("A", buffer.get(4, 2).char)
    }

    @Test
    fun `renders southwest facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.SW, Color.CYAN)

        assertEquals(ICON_FACING_SW, buffer.get(3, 3).char)
        assertEquals("A", buffer.get(4, 2).char)
    }

    @Test
    fun `renders northwest facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, 'A', HexDirection.NW, Color.CYAN)

        assertEquals(ICON_FACING_NW, buffer.get(3, 2).char)
    }
}
