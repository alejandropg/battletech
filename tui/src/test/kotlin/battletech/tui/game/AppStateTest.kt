package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tui.aGameMap
import battletech.tui.game.phase.InitiativePhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.next
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class AppStateTest {

    @Nested
    inner class CurrentPhaseTest {
        @Test
        fun `currentPhase is derived from phase turnPhase`() {
            val app = AppState(
                battletech.tui.aGameState(),
                TurnState.NULL,
                InitiativePhase,
                HexCoordinates(0, 0)
            )
            assertEquals(TurnPhase.INITIATIVE, app.currentPhase)
        }

        @Test
        fun `currentPhase reflects movement when in MovementPhase`() {
            val app = AppState(
                battletech.tui.aGameState(),
                TurnState.NULL,
                MovementPhase.SelectingUnit,
                HexCoordinates(0, 0)
            )
            assertEquals(TurnPhase.MOVEMENT, app.currentPhase)
        }
    }

    @Nested
    inner class NextPhaseTest {
        @Test
        fun `movement advances to weapon attack`() {
            assertEquals(TurnPhase.WEAPON_ATTACK, TurnPhase.MOVEMENT.next)
        }

        @Test
        fun `weapon attack advances to physical attack`() {
            assertEquals(TurnPhase.PHYSICAL_ATTACK, TurnPhase.WEAPON_ATTACK.next)
        }

        @Test
        fun `end advances to initiative`() {
            assertEquals(TurnPhase.INITIATIVE, TurnPhase.END.next)
        }

        @Test
        fun `full phase cycle`() {
            val phases = listOf(
                TurnPhase.INITIATIVE,
                TurnPhase.MOVEMENT,
                TurnPhase.WEAPON_ATTACK,
                TurnPhase.PHYSICAL_ATTACK,
                TurnPhase.HEAT,
                TurnPhase.END,
            )
            var current = TurnPhase.INITIATIVE
            val visited = mutableListOf(current)
            repeat(6) {
                current = current.next
                visited.add(current)
            }
            assertEquals(phases + TurnPhase.INITIATIVE, visited)
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
