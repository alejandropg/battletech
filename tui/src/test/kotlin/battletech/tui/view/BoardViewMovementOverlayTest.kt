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
        val view = BoardView(state, Viewport(0, 0, 36, 20), hexHighlights = highlights)
        val buffer = ScreenBuffer(40, 24)

        view.render(buffer, 0, 0, 40, 24)

        // Content offset +2,+2 for border + padding
        // Hex (1,1) content at (11+2, 8+2) = (13, 10)
        assertEquals(Color.CYAN, buffer.get(13, 10).bg)
        // Hex (2,1) content at (18+2, 6+2) = (20, 8)
        assertEquals(Color.CYAN, buffer.get(20, 8).bg)
    }

    @Test
    fun `path hexes get yellow background`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5))
        val highlights = mapOf(
            HexCoordinates(0, 0) to HexHighlight.PATH,
            HexCoordinates(1, 0) to HexHighlight.PATH,
        )
        val view = BoardView(state, Viewport(0, 0, 36, 20), hexHighlights = highlights)
        val buffer = ScreenBuffer(40, 24)

        view.render(buffer, 0, 0, 40, 24)

        // Content offset +2,+2
        // Hex (0,0) content at (4+2, 2+2) = (6, 4)
        assertEquals(Color.YELLOW, buffer.get(6, 4).bg)
        // Hex (1,0) content at (11+2, 4+2) = (13, 6)
        assertEquals(Color.YELLOW, buffer.get(13, 6).bg)
    }
}
