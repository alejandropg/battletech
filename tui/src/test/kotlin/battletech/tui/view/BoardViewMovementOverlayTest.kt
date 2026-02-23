package battletech.tui.view

import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.hex.HexHighlight
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.model.HexCoordinates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class BoardViewMovementOverlayTest {

    @Test
    fun `reachable hexes show dot marker at center`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5))
        val highlights = mapOf(
            HexCoordinates(1, 1) to HexHighlight.REACHABLE,
            HexCoordinates(2, 1) to HexHighlight.REACHABLE,
        )
        val view = BoardView(state, Viewport(0, 0, 36, 20), hexHighlights = highlights)
        val buffer = ScreenBuffer(40, 24)

        view.render(buffer, 0, 0, 40, 24)

        // Hex center is at x+4, y+2 from hex render origin
        // Hex (1,1) center at (13, 10)
        assertEquals(".", buffer.get(13, 10).char)
        // Hex (2,1) center at (20, 8)
        assertEquals(".", buffer.get(20, 8).char)
    }

    @Test
    fun `path hexes show star marker at center`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5))
        val highlights = mapOf(
            HexCoordinates(0, 0) to HexHighlight.PATH,
            HexCoordinates(1, 0) to HexHighlight.PATH,
        )
        val view = BoardView(state, Viewport(0, 0, 36, 20), hexHighlights = highlights)
        val buffer = ScreenBuffer(40, 24)

        view.render(buffer, 0, 0, 40, 24)

        // Hex (0,0) center at (6, 4)
        assertEquals("*", buffer.get(6, 4).char)
        // Hex (1,0) center at (13, 6)
        assertEquals("*", buffer.get(13, 6).char)
    }
}
