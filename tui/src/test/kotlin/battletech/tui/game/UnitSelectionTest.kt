package battletech.tui.game

import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.MovementImpulse
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.model.HexCoordinates
import battletech.tui.aGameState
import battletech.tui.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class UnitSelectionTest {

    private fun aTurnState(
        activePlayer: PlayerId = PlayerId.PLAYER_1,
        movementOrder: List<MovementImpulse> = listOf(
            MovementImpulse(activePlayer, 2),
        ),
        movedUnitIds: Set<UnitId> = emptySet(),
    ) = TurnState(
        initiativeResult = InitiativeResult(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        ),
        movementOrder = movementOrder,
        movedUnitIds = movedUnitIds,
    )

    @Nested
    inner class SelectableUnitsTest {
        @Test
        fun `returns active player unmoved units`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(1, 0))
            val u3 = aUnit(id = "u3", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
            val gameState = aGameState(units = listOf(u1, u2, u3))
            val turnState = aTurnState(activePlayer = PlayerId.PLAYER_1)

            val result = selectableUnits(gameState, turnState)

            assertEquals(listOf(u1, u2), result)
        }

        @Test
        fun `excludes already moved units`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(1, 0))
            val gameState = aGameState(units = listOf(u1, u2))
            val turnState = aTurnState(
                activePlayer = PlayerId.PLAYER_1,
                movedUnitIds = setOf(UnitId("u1")),
            )

            val result = selectableUnits(gameState, turnState)

            assertEquals(listOf(u2), result)
        }

        @Test
        fun `returns empty when no selectable units`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(u1))
            val turnState = aTurnState(activePlayer = PlayerId.PLAYER_1)

            val result = selectableUnits(gameState, turnState)

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class ValidateUnitSelectionTest {
        @Test
        fun `own unmoved unit is valid`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val turnState = aTurnState(activePlayer = PlayerId.PLAYER_1)

            val result = validateUnitSelection(u1, turnState)

            assertEquals(UnitSelectionResult.VALID, result)
        }

        @Test
        fun `opponent unit is rejected`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 0))
            val turnState = aTurnState(activePlayer = PlayerId.PLAYER_1)

            val result = validateUnitSelection(u1, turnState)

            assertEquals(UnitSelectionResult.NOT_YOUR_UNIT, result)
        }

        @Test
        fun `already moved unit is rejected`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val turnState = aTurnState(
                activePlayer = PlayerId.PLAYER_1,
                movedUnitIds = setOf(UnitId("u1")),
            )

            val result = validateUnitSelection(u1, turnState)

            assertEquals(UnitSelectionResult.ALREADY_MOVED, result)
        }
    }
}
