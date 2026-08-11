package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.query.projectFor
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.hex.HexHighlight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class BoardViewMovementPhaseStateOverlayTest {

    @Test
    fun `reachable hexes show dot marker at center`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5)).projectFor(viewer = null, revealAll = true)
        val highlights = mapOf(
            HexCoordinates(1, 1) to HexHighlight.REACHABLE_WALK,
            HexCoordinates(2, 1) to HexHighlight.REACHABLE_WALK,
        )
        val view = BoardView(state, hexHighlights = highlights)
        val buffer = render(view, 40, 24)

        // Hex center is at x+4, y+2 from hex render origin
        // Hex (1,1) center at (15, 9), including the board's coordinate margins
        assertEquals(".", buffer.get(15, 9).char)
        // Hex (2,1) center at (22, 7), including the board's coordinate margins
        assertEquals(".", buffer.get(22, 7).char)
    }

    @Test
    fun `reachable hex with facing overlay suppresses dot at center`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5)).projectFor(viewer = null, revealAll = true)
        val highlights = mapOf(
            HexCoordinates(1, 1) to HexHighlight.REACHABLE_WALK,
        )
        // SE facing is at offset (6,3), not at center (4,2) — so no arrow overwrites center
        val reachableFacings = mapOf(
            HexCoordinates(1, 1) to setOf(HexDirection.SE),
        )
        val view = BoardView(
            state,
            hexHighlights = highlights,
            reachableFacings = reachableFacings,
        )
        val buffer = render(view, 40, 24)

        // Hex (1,1) center is at (15, 9); the REACHABLE_WALK dot must be suppressed
        assertNotEquals(".", buffer.get(15, 9).char)
    }

    @Test
    fun `path hexes show walk icon even when also in reachable facings`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5)).projectFor(viewer = null, revealAll = true)
        val highlights = mapOf(
            HexCoordinates(1, 0) to HexHighlight.PATH,
        )
        // N facing is at (x+4, y+2) — same as star center; must not overwrite it
        val reachableFacings = mapOf(
            HexCoordinates(1, 0) to setOf(HexDirection.N),
        )
        val view = BoardView(
            state,
            hexHighlights = highlights,
            reachableFacings = reachableFacings,
            movementMode = MovementMode.WALK,
        )
        val buffer = render(view, 40, 24)

        // Hex (1,0) center at (15, 5); N arrow also lands at (15, 5)
        assertEquals(String(Character.toChars(0xF0583)), buffer.get(15, 5).char)
    }

    @Test
    fun `path hexes show walk icon at center`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5)).projectFor(viewer = null, revealAll = true)
        val highlights = mapOf(
            HexCoordinates(0, 0) to HexHighlight.PATH,
            HexCoordinates(1, 0) to HexHighlight.PATH,
        )
        val view = BoardView(
            state,
            hexHighlights = highlights,
            movementMode = MovementMode.WALK,
        )
        val buffer = render(view, 40, 24)

        // Hex (0,0) center at (8, 3), including the board's coordinate margins
        assertEquals(String(Character.toChars(0xF0583)), buffer.get(8, 3).char)
        // Hex (1,0) center at (15, 5), including the board's coordinate margins
        assertEquals(String(Character.toChars(0xF0583)), buffer.get(15, 5).char)
    }

    @Test
    fun `destination hex with all facings and walk mode shows walk icon`() {
        val state = aGameState(map = aGameMap(cols = 5, rows = 5)).projectFor(viewer = null, revealAll = true)
        val allFacings = HexDirection.entries.toSet()
        val reachableFacings = mapOf(
            HexCoordinates(1, 0) to allFacings,
        )
        val view = BoardView(
            state,
            reachableFacings = reachableFacings,
            pathDestination = HexCoordinates(1, 0),
            movementMode = MovementMode.WALK,
        )
        val buffer = render(view, 40, 24)

        // Hex (1,0) center at (15, 5); all-facings destination shows mode icon
        assertEquals(String(Character.toChars(0xF0583)), buffer.get(15, 5).char)
    }
}
