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
        val view = BoardView(state, viewport = Viewport(0, 0, 26, 12))
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Content offset +2,+2 for border + padding
        // Hex at (0,0) should have '/' at charX=0+2, charY=2+2
        assertEquals('/', buffer.get(2, 4).char)
        // Hex at (1,0) should have '/' at charX=8+2, charY=3+2
        assertEquals('/', buffer.get(10, 5).char)
        // Hex at (2,0) should have '/' at charX=14+2, charY=2+2
        assertEquals('/', buffer.get(16, 4).char)
    }

    @Test
    fun `renders unit initial on hex`() {
        val unit = aUnit(name = "Atlas", position = HexCoordinates(0, 0))
        val state = aGameState(units = listOf(unit), map = aGameMap())
        val view = BoardView(state, viewport = Viewport(0, 0, 26, 12))
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Unit initial 'A' at hex center: charX=4+2, charY=3+2
        assertEquals('A', buffer.get(6, 5).char)
    }

    @Test
    fun `scroll offset hides column 0`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 3))
        val view = BoardView(state, viewport = Viewport(1, 0, 26, 12))
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Column 0 should not be rendered in content area - check inside content area
        assertEquals(' ', buffer.get(2, 3).char)
        assertEquals(' ', buffer.get(5, 3).char)
    }

    @Test
    fun `cursor position highlights hex`() {
        val state = aGameState(map = aGameMap())
        val cursor = HexCoordinates(1, 1)
        val view = BoardView(state, viewport = Viewport(0, 0, 26, 12), cursorPosition = cursor)
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Hex at (1,1) border '/' offset by +2,+2
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(10, 9).fg)
    }

    @Test
    fun `highlights map with reachable and path overlays`() {
        val state = aGameState(map = aGameMap())
        val highlights = mapOf(
            HexCoordinates(1, 0) to HexHighlight.REACHABLE,
            HexCoordinates(2, 0) to HexHighlight.PATH,
        )
        val view = BoardView(state, viewport = Viewport(0, 0, 26, 12), hexHighlights = highlights)
        val buffer = ScreenBuffer(30, 16)

        view.render(buffer, 0, 0, 30, 16)

        // Content offset +2,+2
        assertEquals(Color.CYAN, buffer.get(13, 6).bg)
        assertEquals(Color.YELLOW, buffer.get(20, 4).bg)
    }
}
