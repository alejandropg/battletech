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
        val view = BoardView(state, Viewport(0, 0, 40, 20), hexHighlights = highlights)
        val buffer = ScreenBuffer(40, 20)

        view.render(buffer, 0, 0, 40, 20)

        // Hex (1,1) at charX=8, charY=6, content at (11, 7)
        assertEquals(Color.CYAN, buffer.get(11, 7).bg)
        // Hex (2,1) at charX=16, charY=4, content at (19, 5)
        assertEquals(Color.CYAN, buffer.get(19, 5).bg)
    }

    @Test
    fun `path hexes get yellow background`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5))
        val highlights = mapOf(
            HexCoordinates(0, 0) to HexHighlight.PATH,
            HexCoordinates(1, 0) to HexHighlight.PATH,
        )
        val view = BoardView(state, Viewport(0, 0, 40, 20), hexHighlights = highlights)
        val buffer = ScreenBuffer(40, 20)

        view.render(buffer, 0, 0, 40, 20)

        // Hex (0,0) content at (3, 1)
        assertEquals(Color.YELLOW, buffer.get(3, 1).bg)
        // Hex (1,0) at charX=8, charY=2, content at (11, 3)
        assertEquals(Color.YELLOW, buffer.get(11, 3).bg)
    }
}
