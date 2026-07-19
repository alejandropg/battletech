package battletech.tactical.movement

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.query.aUnit
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.MovementProgress
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.TurnState
import battletech.tactical.unit.UnitRoster
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies the server-authoritative destination validation added to
 * [MovementPhaseHandler.validate] for [MoveUnit] commands.
 */
internal class MovementDestinationValidationTest {

    private val handler = MovementPhaseHandler()
    private val roller = DiceRoller.deterministic(1, 1)

    // Unit at (0,0) facing N, walkingMP=4.
    private val mover = aUnit(id = "mover", position = HexCoordinates(0, 0), walkingMP = 4)

    // Grid: columns -1..1, rows -4..1 — enough for a 4-MP walk in any direction.
    private val mapHexes: Map<HexCoordinates, Hex> =
        (-1..1).flatMap { col -> (-4..1).map { row -> HexCoordinates(col, row) } }
            .associateWith { Hex(it) }

    private val state = GameState(units = UnitRoster(listOf(mover)), map = GameMap(mapHexes))

    private fun turn() = TurnState(
        initiative = Initiative(emptyMap(), PlayerId.PLAYER_1, PlayerId.PLAYER_2),
        movement = MovementProgress(
            sequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
        ),
    )

    // -------------------------------------------------------------------------
    // Unreachable position
    // -------------------------------------------------------------------------

    @Test
    fun `MoveUnit to hex outside the map is rejected with DestinationUnreachable`() {
        val farAway = ReachableHex(
            position = HexCoordinates(10, 10),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(MovementStep(HexCoordinates(10, 10), HexDirection.N)),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, farAway, MovementMode.WALK)

        val rejection = handler.validate(command, state, turn())

        assertThat(rejection).isInstanceOf(CommandRejection.DestinationUnreachable::class.java)
        val dr = rejection as CommandRejection.DestinationUnreachable
        assertThat(dr.unitId).isEqualTo(mover.id)
        assertThat(dr.destination).isEqualTo(HexCoordinates(10, 10))
    }

    @Test
    fun `MoveUnit to hex requiring more MP than the unit has is rejected`() {
        // (0,-5) is 5 hops north — beyond walkingMP=4.
        val tooFar = ReachableHex(
            position = HexCoordinates(0, -5),
            facing = HexDirection.N,
            mpSpent = 5,
            path = listOf(
                MovementStep(HexCoordinates(0, -1), HexDirection.N),
                MovementStep(HexCoordinates(0, -2), HexDirection.N),
                MovementStep(HexCoordinates(0, -3), HexDirection.N),
                MovementStep(HexCoordinates(0, -4), HexDirection.N),
                MovementStep(HexCoordinates(0, -5), HexDirection.N),
            ),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, tooFar, MovementMode.WALK)

        val rejection = handler.validate(command, state, turn())

        assertThat(rejection).isInstanceOf(CommandRejection.DestinationUnreachable::class.java)
    }

    // -------------------------------------------------------------------------
    // Tampered path / mpSpent
    // -------------------------------------------------------------------------

    @Test
    fun `MoveUnit with tampered mpSpent is rejected`() {
        // Server computes: (0,0) N → (0,-1) N costs 1 MP, path=[MovementStep(0,-1,N)].
        // Client claims mpSpent=0 (cheaper than the server computed value).
        val tampered = ReachableHex(
            position = HexCoordinates(0, -1),
            facing = HexDirection.N,
            mpSpent = 0,
            path = listOf(MovementStep(HexCoordinates(0, -1), HexDirection.N)),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, tampered, MovementMode.WALK)

        val rejection = handler.validate(command, state, turn())

        assertThat(rejection).isInstanceOf(CommandRejection.DestinationUnreachable::class.java)
    }

    @Test
    fun `MoveUnit with tampered path is rejected`() {
        // Same position/facing/mpSpent as the server value, but path is forged (empty instead of real).
        val tampered = ReachableHex(
            position = HexCoordinates(0, -1),
            facing = HexDirection.N,
            mpSpent = 1,
            path = emptyList(),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, tampered, MovementMode.WALK)

        val rejection = handler.validate(command, state, turn())

        assertThat(rejection).isInstanceOf(CommandRejection.DestinationUnreachable::class.java)
    }

    // -------------------------------------------------------------------------
    // Valid move — accepted and applied with server-authoritative values
    // -------------------------------------------------------------------------

    @Test
    fun `valid MoveUnit with correct server-computed destination is accepted`() {
        // From (0,0) facing N: move north one hex.  Server-computed path has one step.
        val validDestination = ReachableHex(
            position = HexCoordinates(0, -1),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(MovementStep(HexCoordinates(0, -1), HexDirection.N)),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, validDestination, MovementMode.WALK)

        val rejection = handler.validate(command, state, turn())

        assertThat(rejection).isNull()
    }

    @Test
    fun `valid MoveUnit is applied with correct final position and facing`() {
        val validDestination = ReachableHex(
            position = HexCoordinates(0, -1),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(MovementStep(HexCoordinates(0, -1), HexDirection.N)),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, validDestination, MovementMode.WALK)

        val outcome = handler.apply(command, state, turn(), roller)

        val movedUnit = outcome.state.units.byId(mover.id)
        assertThat(movedUnit.position).isEqualTo(HexCoordinates(0, -1))
        assertThat(movedUnit.facing).isEqualTo(HexDirection.N)
    }

    // -------------------------------------------------------------------------
    // Stationary move (0 MP, same position + facing) is always accepted
    // -------------------------------------------------------------------------

    @Test
    fun `stationary MoveUnit (0 MP, same position and facing) is accepted`() {
        val stationary = ReachableHex(
            position = mover.position,
            facing = mover.facing,
            mpSpent = 0,
            path = emptyList(),
        )
        val command = MoveUnit(PlayerId.PLAYER_1, mover.id, stationary, MovementMode.WALK)

        val rejection = handler.validate(command, state, turn())

        assertThat(rejection).isNull()
    }
}
