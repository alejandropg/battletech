package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class InitiativePhaseHandlerTest {

    private val emptyState = GameState(
        units = emptyList(),
        map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)))),
    )

    @Test
    fun `onEntry preserves the incoming turnNumber on the rebuilt TurnState`() {
        val handler = InitiativePhaseHandler()
        val incoming = TurnState.NULL.copy(turnNumber = 5)

        val outcome = handler.onEntry(emptyState, incoming, DiceRoller.seeded(1))

        assertThat(outcome.turn.turnNumber).isEqualTo(5)
    }
}
