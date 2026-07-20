package battletech.tactical.movement

import battletech.tactical.unit.MovementThisTurn

import battletech.tactical.model.MovementMode

import battletech.tactical.attack.attackerMovementModifier
import battletech.tactical.attack.targetMovementModifier
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.Hex
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

    /**
     * Small grid around (0,0) that gives the reachability calculator enough room
     * for a 4-MP walk straight north and a turn-in-place.
     */
    private val smallGrid: Map<HexCoordinates, Hex> =
        (-1..1).flatMap { col -> (-4..1).map { row -> HexCoordinates(col, row) } }
            .associateWith { Hex(it) }

    @Test
    fun `moving records the mode and hexes moved on the unit`() {
        val mover = aUnit(id = "mover", position = HexCoordinates(0, 0))
        val state = aGameState(units = listOf(mover), hexes = smallGrid)
        // Server-computable path: move N three times (0,-1),(0,-2),(0,-3) then turn NE in-place.
        // The turn-in-place step must NOT count as a hex entered, giving hexesMoved == 3.
        val destination = ReachableHex(
            position = HexCoordinates(0, -3),
            facing = HexDirection.NE,
            mpSpent = 4,
            path = listOf(
                MovementStep(HexCoordinates(0, -1), HexDirection.N),
                MovementStep(HexCoordinates(0, -2), HexDirection.N),
                MovementStep(HexCoordinates(0, -3), HexDirection.N),
                MovementStep(HexCoordinates(0, -3), HexDirection.NE),
            ),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, destination, MovementMode.WALK)

        val outcome = handler.apply(command, state, turnFor(PlayerId.PLAYER_1), roller)

        val moved = outcome.state.units.byId(mover.id)
        assertThat(moved.movementThisTurn).isEqualTo(MovementThisTurn.Moved(MovementMode.WALK, 3))
    }

    @Test
    fun `entering the movement phase resets movement records to stationary`() {
        val mover = aUnit(id = "mover", position = HexCoordinates(0, 0))
            .copy(movementThisTurn = MovementThisTurn.Moved(MovementMode.RUN, 6))
        val state = aGameState(units = listOf(mover))

        val outcome = handler.onEntry(state, turnFor(PlayerId.PLAYER_1), roller)

        val reset = outcome.state.units.byId(mover.id)
        assertThat(reset.movementThisTurn).isEqualTo(MovementThisTurn.Stationary)
    }

    // -------------------------------------------------------------------------
    // Stationary (0 MP) vs turn-in-place (>=1 MP, 0 hexes) — docs/rules/to-hit-modifiers.md
    // "Stationary attacker -> +0"; a turn-in-place still costs MP and stays Moved(mode, 0).
    // -------------------------------------------------------------------------

    @Test
    fun `a genuine 0-MP stay-put is recorded as Stationary, generates no movement heat, and contributes zero to-hit`() {
        val mover = aUnit(id = "mover", position = HexCoordinates(0, 0))
        val state = aGameState(units = listOf(mover), hexes = smallGrid)
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, MovementRules.stationaryHex(mover), MovementMode.WALK)

        val outcome = handler.apply(command, state, turnFor(PlayerId.PLAYER_1), roller)

        val moved = outcome.state.units.byId(mover.id)
        assertThat(moved.movementThisTurn).isEqualTo(MovementThisTurn.Stationary)
        assertThat(moved.heatGeneratedThisTurn).isEmpty()
        assertThat(attackerMovementModifier(moved.movementThisTurn)).isEqualTo(0)
        assertThat(targetMovementModifier(moved.movementThisTurn)).isEqualTo(0)
    }

    @Test
    fun `a turn-in-place that spends MP but enters no hex stays Moved(mode, 0)`() {
        val mover = aUnit(id = "mover", position = HexCoordinates(0, 0))
        val state = aGameState(units = listOf(mover), hexes = smallGrid)
        // Same hex, new facing — costs 1 MP to turn, unlike a free torso twist.
        val turnInPlace = ReachableHex(
            position = mover.position,
            facing = HexDirection.NE,
            mpSpent = 1,
            path = listOf(MovementStep(mover.position, HexDirection.NE)),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, turnInPlace, MovementMode.WALK)

        val outcome = handler.apply(command, state, turnFor(PlayerId.PLAYER_1), roller)

        val moved = outcome.state.units.byId(mover.id)
        assertThat(moved.movementThisTurn).isEqualTo(MovementThisTurn.Moved(MovementMode.WALK, 0))
    }
}
