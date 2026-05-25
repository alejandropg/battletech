package battletech.tui.game

import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.TurnState
import battletech.tactical.unit.UnitId
import battletech.tui.aGameState
import battletech.tui.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UnitSelectionTest {

    private fun aTurnState(
        activePlayer: PlayerId = PlayerId.PLAYER_1,
        movementOrder: List<Impulse> = listOf(
            Impulse(activePlayer, 2),
        ),
        movedUnitIds: Set<UnitId> = emptySet(),
    ) = TurnState(
        initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        ),
        movementSequence = ImpulseSequence(movementOrder),
        movedUnitIds = movedUnitIds,
    )

    @Test
    fun `returns active player unmoved units`() {
        val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
        val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(1, 0))
        val u3 = aUnit(id = "u3", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
        val gameState = aGameState(units = listOf(u1, u2, u3))
        val turnState = aTurnState(activePlayer = PlayerId.PLAYER_1)

        val result = turnState.selectableUnits(gameState)

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

        val result = turnState.selectableUnits(gameState)

        assertEquals(listOf(u2), result)
    }

    @Test
    fun `returns empty when no selectable units`() {
        val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 0))
        val gameState = aGameState(units = listOf(u1))
        val turnState = aTurnState(activePlayer = PlayerId.PLAYER_1)

        val result = turnState.selectableUnits(gameState)

        assertTrue(result.isEmpty())
    }
}
