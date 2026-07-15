package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachableHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SubscriptionTest {

    private val mech1 = aMech("m1", PlayerId.PLAYER_1, HexCoordinates(0, 0))
    private val mech2 = aMech("m2", PlayerId.PLAYER_2, HexCoordinates(3, 0))

    @Test
    fun `subscriber receives events emitted by submitCommand`() {
        val session = sessionInMovement()
        val received = mutableListOf<GameEvent>()
        session.subscribe { received += it }

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
        session.subscribe { received += it }

        session.advance()

        assertThat(received.filterIsInstance<InitiativeRolled>()).hasSize(1)
        assertThat(received.filterIsInstance<PhaseChanged>()).hasSize(1)
    }

    @Test
    fun `unsubscribe stops delivery`() {
        val session = sessionInMovement()
        val received = mutableListOf<GameEvent>()
        val subscription = session.subscribe { received += it }

        subscription.unsubscribe()
        session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )

        assertThat(received).isEmpty()
    }

    @Test
    fun `unsubscribe is idempotent`() {
        val session = sessionInMovement()
        val subscription = session.subscribe { /* noop */ }

        subscription.unsubscribe()
        subscription.unsubscribe() // must not throw
    }

    @Test
    fun `multiple subscribers for the same player both receive events`() {
        val session = sessionInMovement()
        val a = mutableListOf<GameEvent>()
        val b = mutableListOf<GameEvent>()
        session.subscribe { a += it }
        session.subscribe { b += it }

        session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )

        assertThat(a.filterIsInstance<UnitMoved>()).hasSize(1)
        assertThat(b.filterIsInstance<UnitMoved>()).hasSize(1)
    }

    @Test
    fun `multiple subscribers each receive every event`() {
        val session = sessionInMovement()
        val first = mutableListOf<GameEvent>()
        val second = mutableListOf<GameEvent>()
        session.subscribe { first += it }
        session.subscribe { second += it }

        session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )

        // subscribe() is a raw, unfiltered feed, not the redaction seam — both observers
        // see the same event. Per-player redaction happens at stateFor/logFor instead.
        assertThat(first.filterIsInstance<UnitMoved>()).hasSize(1)
        assertThat(second.filterIsInstance<UnitMoved>()).hasSize(1)
    }

    @Test
    fun `listener unsubscribing itself mid-dispatch does not break delivery`() {
        val session = sessionInMovement()
        val received = mutableListOf<GameEvent>()
        var subscription: Subscription? = null
        subscription = session.subscribe { ev ->
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

    private fun sessionInMovement(): BattleSession = BattleSession(
        initialGameState = GameState(listOf(mech1, mech2), GameMap(hexesFor(listOf(mech1, mech2)))),
        initialTurnState = aMovementTurn(),
        roller = DiceRoller.seeded(42),
        initialPhase = TurnPhase.MOVEMENT,
        initialNeedsOnEntry = false,
    )

    // mech1 is at (0,0) facing N; its N-neighbour (0,-1) costs exactly 1 MP.
    private fun aReachableHex(): ReachableHex = ReachableHex(
        position = HexCoordinates(0, -1),
        facing = HexDirection.N,
        mpSpent = 1,
        path = listOf(MovementStep(HexCoordinates(0, -1), HexDirection.N)),
    )
}
