package battletech.tui.view

import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.hex.HexHighlight
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.model.HexCoordinates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class BoardViewMovementOverlayTest {

    @Test
    fun `reachable hexes get cyan background`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5))
        val highlights = mapOf(
            HexCoordinates(1, 1) to HexHighlight.REACHABLE,
            HexCoordinates(2, 1) to HexHighlight.REACHABLE,
        )
        val view = BoardView(state, Viewport(0, 0, 40, 24), hexHighlights = highlights)
        val buffer = ScreenBuffer(40, 24)

        view.render(buffer, 0, 0, 40, 24)

        // Hex (1,1) at charX=7, charY=6, content at (11, 8)
        assertEquals(Color.CYAN, buffer.get(11, 8).bg)
        // Hex (2,1) at charX=14, charY=4, content at (18, 6)
        assertEquals(Color.CYAN, buffer.get(18, 6).bg)
    }

    @Test
    fun `path hexes get yellow background`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5))
        val highlights = mapOf(
            HexCoordinates(0, 0) to HexHighlight.PATH,
            HexCoordinates(1, 0) to HexHighlight.PATH,
        )
        val view = BoardView(state, Viewport(0, 0, 40, 24), hexHighlights = highlights)
        val buffer = ScreenBuffer(40, 24)

        view.render(buffer, 0, 0, 40, 24)

        // Hex (0,0) content at (4, 2)
        assertEquals(Color.YELLOW, buffer.get(4, 2).bg)
        // Hex (1,0) at charX=7, charY=2, content at (11, 4)
        assertEquals(Color.YELLOW, buffer.get(11, 4).bg)
    }
}
