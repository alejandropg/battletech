package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
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
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BattleSessionTest {

    private val mech1 = aMech(id = "m1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
    private val mech2 = aMech(id = "m2", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 0))

    private fun sessionInMovement(
        units: List<CombatUnit> = listOf(mech1, mech2),
        turn: TurnState = aMovementTurn(),
        roller: DiceRoller = DiceRoller.seeded(42),
    ): BattleSession = BattleSession(
        initialGameState = GameState(units, GameMap(hexesFor(units))),
        initialTurnState = turn,
        roller = roller,
        initialPhase = TurnPhase.MOVEMENT,
        initialNeedsOnEntry = false,
    )

    @Test
    fun `MoveUnit applies and advances turn state`() {
        val session = sessionInMovement()
        // mech1 is at (0,0) facing N; the N-neighbour is (0,-1).
        // Server-computable path: move forward one hex, 1 MP, no turns.
        val destination = ReachableHex(
            position = HexCoordinates(0, -1),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(MovementStep(HexCoordinates(0, -1), HexDirection.N)),
        )

        val result = session.submitCommand(
            MoveUnit(playerId = PlayerId.PLAYER_1, unitId = mech1.id, destination = destination, mode = MovementMode.WALK),
        )

        assertThat(result).isInstanceOf(CommandResult.Accepted::class.java)
        val accepted = result as CommandResult.Accepted
        val moved = accepted.events.filterIsInstance<UnitMoved>().single()
        assertThat(moved.unitId).isEqualTo(mech1.id)
        assertThat(moved.from).isEqualTo(HexCoordinates(0, 0))
        assertThat(moved.to).isEqualTo(HexCoordinates(0, -1))
        assertThat(moved.mode).isEqualTo(MovementMode.WALK)
        assertThat(session.gameState.unitById(mech1.id)!!.position).isEqualTo(HexCoordinates(0, -1))
        assertThat(session.turnState.movement.movedUnitIds).contains(mech1.id)
    }

    @Test
    fun `MoveUnit rejects UnknownUnit`() {
        val session = sessionInMovement()
        val result = session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, UnitId("ghost"), aReachableHex(), MovementMode.WALK),
        )
        val rejected = result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.UnknownUnit::class.java)
    }

    @Test
    fun `MoveUnit rejects when player does not own the unit`() {
        val session = sessionInMovement()
        val result = session.submitCommand(
            MoveUnit(PlayerId.PLAYER_2, mech1.id, aReachableHex(), MovementMode.WALK),
        )
        val rejected = result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.NotYourTurn::class.java)
    }

    @Test
    fun `MoveUnit rejects when the unit has already moved this turn`() {
        val session = sessionInMovement(
            turn = aMovementTurn().copy(movement = aMovementTurn().movement.copy(movedUnitIds = setOf(mech1.id))),
        )
        val result = session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )
        val rejected = result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.UnitAlreadyActed::class.java)
    }

    @Test
    fun `submitCommand to wrong phase is rejected with WrongPhase`() {
        // Heat phase doesn't accept any commands
        val session = BattleSession(
            initialGameState = GameState(listOf(mech1, mech2), GameMap(hexesFor(listOf(mech1, mech2)))),
            initialTurnState = aMovementTurn(),
            roller = DiceRoller.seeded(42),
            initialPhase = TurnPhase.HEAT,
            initialNeedsOnEntry = false,
        )
        val result = session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )
        val rejected = result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.WrongPhase::class.java)
        assertThat((rejected.reason as CommandRejection.WrongPhase).actual).isEqualTo(TurnPhase.HEAT)
    }

    @Test
    fun `CommitAttackImpulse records declarations and applies torso facings (non-final, weapon phase)`() {
        val turn = anAttackTurn()
        val session = BattleSession(
            initialGameState = GameState(listOf(mech1, mech2), GameMap(hexesFor(listOf(mech1, mech2)))),
            initialTurnState = turn,
            roller = DiceRoller.seeded(42),
            initialPhase = TurnPhase.WEAPON_ATTACK,
            initialNeedsOnEntry = false,
        )
        val decls = listOf(AttackDeclaration(mech1.id, mech2.id, 0, true))

        val result = session.submitCommand(
            CommitAttackImpulse(
                playerId = PlayerId.PLAYER_1,
                declarations = decls,
                torsoFacings = mapOf(mech1.id to HexDirection.NE),
            ),
        )

        val accepted = result as CommandResult.Accepted
        assertThat(accepted.events.filterIsInstance<TorsoFacingsApplied>()).hasSize(1)
        assertThat(accepted.events.filterIsInstance<AttackDeclarationsRecorded>()).hasSize(1)
        assertThat(accepted.events.filterIsInstance<AttacksResolved>()).isEmpty()
        assertThat(accepted.events.filterIsInstance<PhaseChanged>()).isEmpty()

        assertThat(session.turnState.attack.weaponDeclarations).containsExactlyElementsOf(decls)
        assertThat(session.gameState.unitById(mech1.id)!!.torsoFacing).isEqualTo(HexDirection.NE)
    }

    @Test
    fun `CommitAttackImpulse resolves attacks on the final weapon-phase impulse and advances to physical`() {
        val attacker = aMech(id = "a", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
        val target = aMech(id = "t", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 0))
        val turn = anAttackTurn(attackOrder = listOf(Impulse(PlayerId.PLAYER_1, 1)))
        val session = BattleSession(
            initialGameState = GameState(listOf(attacker, target), GameMap(hexesFor(listOf(attacker, target)))),
            initialTurnState = turn,
            roller = DiceRoller.seeded(42),
            initialPhase = TurnPhase.WEAPON_ATTACK,
            initialNeedsOnEntry = false,
        )

        val result = session.submitCommand(
            CommitAttackImpulse(
                playerId = PlayerId.PLAYER_1,
                declarations = listOf(AttackDeclaration(attacker.id, target.id, 0, true)),
                torsoFacings = emptyMap(),
            ),
        )

        val accepted = result as CommandResult.Accepted
        assertThat(accepted.events.filterIsInstance<AttacksResolved>()).hasSize(1)
        // Phase auto-advanced after the handler reported complete.
        assertThat(accepted.events.filterIsInstance<PhaseChanged>()).hasSize(1)
        assertThat(session.currentPhase).isEqualTo(TurnPhase.PHYSICAL_ATTACK)
        assertThat(session.turnState.attack.weaponDeclarations).isEmpty()
    }

    @Test
    fun `advance bootstraps initiative on construction`() {
        val session = BattleSession(
            initialGameState = GameState(listOf(mech1, mech2), GameMap(hexesFor(listOf(mech1, mech2)))),
            initialTurnState = TurnState.NULL,
            roller = DiceRoller.seeded(42),
        )
        // Tick once: Initiative.onEntry runs, isComplete=true, advance to MOVEMENT
        val events = session.advance()
        assertThat(events.filterIsInstance<InitiativeRolled>()).hasSize(1)
        assertThat(events.filterIsInstance<PhaseChanged>()).hasSize(1)
        assertThat(session.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(session.turnState.movement.sequence.order).isNotEmpty
    }

    @Test
    fun `viewFor returns a PlayerView scoped to the requested player`() {
        val session = sessionInMovement()
        val view = session.viewFor(PlayerId.PLAYER_1)
        assertThat(view.playerId).isEqualTo(PlayerId.PLAYER_1)
        assertThat(view.state.units).containsExactlyElementsOf(session.gameState.units)
    }

    @Test
    fun `annotate appends a log entry at the current turn number and notifies both players' listeners`() {
        val session = sessionInMovement()
        val p1Events = mutableListOf<GameEvent>()
        val p2Events = mutableListOf<GameEvent>()
        session.subscribe(PlayerId.PLAYER_1) { p1Events += it }
        session.subscribe(PlayerId.PLAYER_2) { p2Events += it }
        val notice = SessionNotice("Opponent connected")

        session.annotate(notice)

        assertThat(session.gameLog.snapshot()).contains(LogEntry(session.turnState.turnNumber, notice))
        assertThat(p1Events).containsExactly(notice)
        assertThat(p2Events).containsExactly(notice)
    }

    // ---------- helpers ----------

    private fun aReachableHex(): ReachableHex = ReachableHex(
        position = HexCoordinates(1, 0),
        facing = HexDirection.N,
        mpSpent = 1,
        path = emptyList(),
    )
}
