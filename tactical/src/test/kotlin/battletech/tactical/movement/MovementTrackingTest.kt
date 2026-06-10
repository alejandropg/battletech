package battletech.tactical.movement

import battletech.tactical.unit.MovementThisTurn

import battletech.tactical.model.MovementMode

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.MovementProgress
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.TurnState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MovementTrackingTest {

    private val handler = MovementPhaseHandler()
    private val roller = DiceRoller.deterministic(1, 1)

    private fun turnFor(player: PlayerId) = TurnState(
        initiative = Initiative(emptyMap(), PlayerId.PLAYER_1, PlayerId.PLAYER_2),
        movement = MovementProgress(sequence = ImpulseSequence(listOf(Impulse(player, 1)))),
    )

    @Test
    fun `moving records the mode and hexes moved on the unit`() {
        val mover = aUnit(id = "mover", position = HexCoordinates(0, 0))
        val state = aGameState(units = listOf(mover))
        // Path of three hexes plus a turn-in-place step (which must NOT count as a hex).
        val destination = ReachableHex(
            position = HexCoordinates(3, 0),
            facing = HexDirection.NE,
            mpSpent = 4,
            path = listOf(
                MovementStep(HexCoordinates(1, 0), HexDirection.N),
                MovementStep(HexCoordinates(2, 0), HexDirection.N),
                MovementStep(HexCoordinates(3, 0), HexDirection.N),
                MovementStep(HexCoordinates(3, 0), HexDirection.NE),
            ),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, destination, MovementMode.WALK)

        val outcome = handler.apply(command, state, turnFor(PlayerId.PLAYER_1), roller)

        val moved = outcome.state.unitById(mover.id)!!
        assertThat(moved.movementThisTurn.mode).isEqualTo(MovementMode.WALK)
        assertThat(moved.movementThisTurn.hexesMoved).isEqualTo(3)
    }

    @Test
    fun `entering the movement phase resets movement records to stationary`() {
        val mover = aUnit(id = "mover", position = HexCoordinates(0, 0))
            .copy(movementThisTurn = MovementThisTurn(MovementMode.RUN, 6))
        val state = aGameState(units = listOf(mover))

        val outcome = handler.onEntry(state, turnFor(PlayerId.PLAYER_1), roller)

        val reset = outcome.state.unitById(mover.id)!!
        assertThat(reset.movementThisTurn).isEqualTo(MovementThisTurn.STATIONARY)
    }
}
