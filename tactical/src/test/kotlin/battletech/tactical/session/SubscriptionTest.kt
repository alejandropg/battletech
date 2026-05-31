package battletech.tactical.session

import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachableHex
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.InternalStructureLayout
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapons
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SubscriptionTest {

    private val mech1 = aMech("m1", PlayerId.PLAYER_1, HexCoordinates(0, 0))
    private val mech2 = aMech("m2", PlayerId.PLAYER_2, HexCoordinates(3, 0))

    @Test
    fun `subscriber receives events emitted by submitCommand`() {
        val session = sessionInMovement()
        val received = mutableListOf<GameEvent>()
        session.subscribe(PlayerId.PLAYER_1) { received += it }

        session.submitCommand(
            MoveUnit(
                playerId = PlayerId.PLAYER_1,
                unitId = mech1.id,
                destination = aReachableHex(),
                mode = MovementMode.WALK,
            ),
        )

        assertThat(received.filterIsInstance<UnitMoved>()).hasSize(1)
    }

    @Test
    fun `subscriber receives events emitted by advance kickstart`() {
        val session = BattleSession(
            initialGameState = GameState(listOf(mech1, mech2), GameMap(hexesFor(listOf(mech1, mech2)))),
            initialTurnState = TurnState.NULL,
            roller = DiceRoller.seeded(42),
        )
        val received = mutableListOf<GameEvent>()
        session.subscribe(PlayerId.PLAYER_1) { received += it }

        session.advance()

        assertThat(received.filterIsInstance<InitiativeRolled>()).hasSize(1)
        assertThat(received.filterIsInstance<PhaseChanged>()).hasSize(1)
    }

    @Test
    fun `unsubscribe stops delivery`() {
        val session = sessionInMovement()
        val received = mutableListOf<GameEvent>()
        val subscription = session.subscribe(PlayerId.PLAYER_1) { received += it }

        subscription.unsubscribe()
        session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )

        assertThat(received).isEmpty()
    }

    @Test
    fun `unsubscribe is idempotent`() {
        val session = sessionInMovement()
        val subscription = session.subscribe(PlayerId.PLAYER_1) { /* noop */ }

        subscription.unsubscribe()
        subscription.unsubscribe() // must not throw
    }

    @Test
    fun `multiple subscribers for the same player both receive events`() {
        val session = sessionInMovement()
        val a = mutableListOf<GameEvent>()
        val b = mutableListOf<GameEvent>()
        session.subscribe(PlayerId.PLAYER_1) { a += it }
        session.subscribe(PlayerId.PLAYER_1) { b += it }

        session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )

        assertThat(a.filterIsInstance<UnitMoved>()).hasSize(1)
        assertThat(b.filterIsInstance<UnitMoved>()).hasSize(1)
    }

    @Test
    fun `subscribers for different players each receive the same permissive event stream`() {
        val session = sessionInMovement()
        val p1 = mutableListOf<GameEvent>()
        val p2 = mutableListOf<GameEvent>()
        session.subscribe(PlayerId.PLAYER_1) { p1 += it }
        session.subscribe(PlayerId.PLAYER_2) { p2 += it }

        session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )

        // EventVisibility is permissive by default — both observers see the event.
        assertThat(p1.filterIsInstance<UnitMoved>()).hasSize(1)
        assertThat(p2.filterIsInstance<UnitMoved>()).hasSize(1)
    }

    @Test
    fun `listener unsubscribing itself mid-dispatch does not break delivery`() {
        val session = sessionInMovement()
        val received = mutableListOf<GameEvent>()
        var subscription: Subscription? = null
        subscription = session.subscribe(PlayerId.PLAYER_1) { ev ->
            received += ev
            subscription?.unsubscribe()
        }

        session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )

        // First event reaches the listener; after self-unsubscribe further
        // events in the same dispatch loop are not delivered to it. The
        // dispatch itself must not throw.
        assertThat(received).isNotEmpty
    }

    // ---------- helpers ----------

    private fun sessionInMovement(): BattleSession {
        val turn = TurnState(
            initiative = Initiative(
                rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
                loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
            ),
            movementSequence = ImpulseSequence(
                listOf(Impulse(PlayerId.PLAYER_1, 1), Impulse(PlayerId.PLAYER_2, 1)),
            ),
        )
        return BattleSession(
            initialGameState = GameState(listOf(mech1, mech2), GameMap(hexesFor(listOf(mech1, mech2)))),
            initialTurnState = turn,
            roller = DiceRoller.seeded(42),
            initialPhase = TurnPhase.MOVEMENT,
            initialNeedsOnEntry = false,
        )
    }

    private fun aMech(id: String, owner: PlayerId, position: HexCoordinates): CombatUnit = CombatUnit(
        id = UnitId(id),
        owner = owner,
        name = id,
        tonnage = 50,
        gunnerySkill = 4,
        pilotingSkill = 5,
        weapons = listOf(Weapons.mediumLaser()),
        position = position,
        facing = HexDirection.N,
        torsoFacing = HexDirection.N,
        walkingMP = 4,
        runningMP = 6,
        jumpMP = 0,
        currentHeat = 0,
        heatSinkCapacity = 10,
        armor = ArmorLayout(
            head = 9,
            centerTorso = 30, centerTorsoRear = 10,
            leftTorso = 25, leftTorsoRear = 8,
            rightTorso = 25, rightTorsoRear = 8,
            leftArm = 20, rightArm = 20,
            leftLeg = 25, rightLeg = 25,
        ),
        internalStructure = InternalStructureLayout(
            head = 3,
            centerTorso = 31,
            leftTorso = 21,
            rightTorso = 21,
            leftArm = 17,
            rightArm = 17,
            leftLeg = 21,
            rightLeg = 21,
        ),
    )

    private fun hexesFor(units: List<CombatUnit>): Map<HexCoordinates, Hex> {
        val coords = units.flatMap { u ->
            listOf(u.position) + HexDirection.entries.map { u.position.neighbor(it) }
        }
        return coords.distinct().associateWith { Hex(it) }
    }

    private fun aReachableHex(): ReachableHex = ReachableHex(
        position = HexCoordinates(1, 0),
        facing = HexDirection.N,
        mpSpent = 1,
        path = listOf(
            MovementStep(HexCoordinates(0, 0), HexDirection.N),
            MovementStep(HexCoordinates(1, 0), HexDirection.NE),
        ),
    )
}
