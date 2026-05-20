package battletech.tui.game

import battletech.tactical.model.TurnPhase
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.session.TurnState
import battletech.tui.aGameMap
import battletech.tui.game.phase.MovementPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class AppStateTest {

    @Nested
    inner class CurrentPhaseTest {
        @Test
        fun `currentPhase reflects movement when in MovementPhase`() {
            val app = AppState(
                battletech.tui.aGameState(),
                TurnState.NULL,
                MovementPhase.SelectingUnit,
                HexCoordinates(0, 0),
            )
            assertEquals(TurnPhase.MOVEMENT, app.currentPhase)
        }
    }

    @Nested
    inner class MoveCursorTest {
        @Test
        fun `moves to neighbor within bounds`() {
            val map = aGameMap(cols = 5, rows = 5)
            val result = moveCursor(HexCoordinates(2, 2), HexDirection.N, map)
            assertEquals(HexCoordinates(2, 1), result)
        }

        @Test
        fun `stays in place when out of bounds`() {
            val map = aGameMap(cols = 3, rows = 3)
            val result = moveCursor(HexCoordinates(0, 0), HexDirection.N, map)
            assertEquals(HexCoordinates(0, 0), result)
        }
    }
}
