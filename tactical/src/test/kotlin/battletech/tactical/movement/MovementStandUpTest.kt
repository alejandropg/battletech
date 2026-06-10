package battletech.tactical.movement

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.MovementProgress
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.StandUp
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitStoodUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MovementStandUpTest {

    private val handler = MovementPhaseHandler()

    private val prone = aUnit(id = "mech", pilotingSkill = 5, position = HexCoordinates(0, 0)).copy(isProne = true)

    private fun turn() = TurnState(
        initiative = Initiative(emptyMap(), PlayerId.PLAYER_1, PlayerId.PLAYER_2),
        movement = MovementProgress(sequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1)))),
    )

    private fun standUp() = StandUp(PlayerId.PLAYER_1, prone.id)

    @Test
    fun `a successful stand-up clears prone without consuming the activation`() {
        val state = aGameState(units = listOf(prone))
        val roller = DiceRoller.deterministic(6, 6) // 12 >= 5, passes

        val outcome = handler.apply(standUp(), state, turn(), roller)

        assertThat(outcome.state.unitById(prone.id)!!.isProne).isFalse()
        val stood = outcome.events.filterIsInstance<UnitStoodUp>().single()
        assertThat(stood.stoodUp).isTrue()
        // Activation not consumed: the unit may still move this impulse.
        assertThat(outcome.turn.movement.movedUnitIds).doesNotContain(prone.id)
    }

    @Test
    fun `a failed stand-up keeps the unit prone and consumes the activation`() {
        val state = aGameState(units = listOf(prone))
        val roller = DiceRoller.deterministic(1, 1) // 2 < 5, fails

        val outcome = handler.apply(standUp(), state, turn(), roller)

        assertThat(outcome.state.unitById(prone.id)!!.isProne).isTrue()
        assertThat(outcome.events.filterIsInstance<UnitStoodUp>().single().stoodUp).isFalse()
        assertThat(outcome.turn.movement.movedUnitIds).contains(prone.id)
    }

    @Test
    fun `moving a prone unit is rejected`() {
        val state = aGameState(units = listOf(prone))
        val destination = ReachableHex(HexCoordinates(1, 0), HexDirection.N, 1, listOf(MovementStep(HexCoordinates(1, 0), HexDirection.N)))
        val command = MoveUnit(PlayerId.PLAYER_1, prone.id, destination, MovementMode.WALK)

        val rejection = handler.validate(command, state, turn())

        assertThat(rejection).isInstanceOf(CommandRejection.UnitProne::class.java)
    }

    @Test
    fun `standing a unit that is not prone is rejected`() {
        val standing = aUnit(id = "mech", position = HexCoordinates(0, 0)) // not prone
        val state = aGameState(units = listOf(standing))

        val rejection = handler.validate(standUp(), state, turn())

        assertThat(rejection).isInstanceOf(CommandRejection.UnitNotProne::class.java)
    }
}
