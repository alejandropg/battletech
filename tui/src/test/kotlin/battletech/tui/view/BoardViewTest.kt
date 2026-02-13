package battletech.tui.view

import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.hex.HexHighlight
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.model.HexCoordinates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class BoardViewTest {

    @Test
    fun `renders hex borders for a 3x3 map`() {
        val state = aGameState(map = aGameMap(cols = 3, rows = 3))
        val view = BoardView(state, viewport = Viewport(0, 0, 30, 16))
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Hex at (0,0) should have '/' at charX=0, charY=1
        assertEquals('/', buffer.get(0, 1).char)
        // Hex at (1,0) should have '/' at charX=8, charY=3 (odd col offset +2)
        assertEquals('/', buffer.get(8, 3).char)
        // Hex at (2,0) should have '/' at charX=16, charY=1
        assertEquals('/', buffer.get(16, 1).char)
    }

    @Test
    fun `renders unit initial on hex`() {
        val unit = aUnit(name = "Atlas", position = HexCoordinates(0, 0))
        val state = aGameState(units = listOf(unit), map = aGameMap())
        val view = BoardView(state, viewport = Viewport(0, 0, 30, 16))
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Unit initial 'A' at hex center: charX=0+3, charY=0+2
        assertEquals('A', buffer.get(3, 2).char)
    }

    @Test
    fun `scroll offset hides column 0`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 3))
        val view = BoardView(state, viewport = Viewport(1, 0, 30, 16))
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Column 0 should not be rendered - default cells
        assertEquals(' ', buffer.get(0, 1).char)
        assertEquals(' ', buffer.get(3, 1).char)
    }

    @Test
    fun `cursor position highlights hex`() {
        val state = aGameState(map = aGameMap())
        val cursor = HexCoordinates(1, 1)
        val view = BoardView(state, viewport = Viewport(0, 0, 30, 16), cursorPosition = cursor)
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Hex at (1,1) → charX=8, charY=6 (row=1*4 + odd_offset=2)
        // Border '/' at (8, 7) should be bright yellow
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(8, 7).fg)
    }

    @Test
    fun `highlights map with reachable and path overlays`() {
        val state = aGameState(map = aGameMap())
        val highlights = mapOf(
            HexCoordinates(1, 0) to HexHighlight.REACHABLE,
            HexCoordinates(2, 0) to HexHighlight.PATH,
        )
        val view = BoardView(state, viewport = Viewport(0, 0, 30, 16), hexHighlights = highlights)
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Hex (1,0) content area at charX=8+3, charY=2+1=3 should be cyan
        assertEquals(Color.CYAN, buffer.get(11, 3).bg)
        // Hex (2,0) content area at charX=16+3, charY=0+1=1 should be yellow
        assertEquals(Color.YELLOW, buffer.get(19, 1).bg)
    }
}
