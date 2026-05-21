package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EndPhaseHandlerTest {

    private val emptyState = GameState(
        units = emptyList(),
        map = GameMap(mapOf(HexCoordinates(0, 0) to battletech.tactical.model.Hex(HexCoordinates(0, 0)))),
    )

    @Test
    fun `onEntry emits TurnEnded with the current turn number`() {
        val handler = EndPhaseHandler()
        val turn = TurnState.NULL.copy(turnNumber = 4)

        val outcome = handler.onEntry(emptyState, turn, DiceRoller.seeded(1))

        val turnEnded = outcome.events.filterIsInstance<TurnEnded>().single()
        assertThat(turnEnded.turnNumber).isEqualTo(4)
    }

    @Test
    fun `onEntry advances turnNumber in the outcome turn state`() {
        val handler = EndPhaseHandler()
        val turn = TurnState.NULL.copy(turnNumber = 4)

        val outcome = handler.onEntry(emptyState, turn, DiceRoller.seeded(1))

        assertThat(outcome.turn.turnNumber).isEqualTo(5)
    }
}
