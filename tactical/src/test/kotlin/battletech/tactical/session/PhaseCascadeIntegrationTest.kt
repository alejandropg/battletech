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
import battletech.tactical.unit.UnitRoster
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Integration test pinning the cross-phase cascade invariants of [BattleSession].
 *
 * Drives one full turn (MOVEMENT → WEAPON_ATTACK → PHYSICAL_ATTACK → HEAT → END)
 * plus the start of turn 2 through [BattleSession.submitCommand] only — no direct
 * [TurnState] manipulation. Units are placed far apart so empty attack declarations
 * are valid throughout.
 *
 * Invariants pinned:
 *
 * I1 – [advance] fires INITIATIVE.onEntry and cascades to MOVEMENT; movement sequence
 *       is seeded and [InitiativeRolled] + [PhaseChanged] are observed by a subscriber.
 *
 * I2 – Active movement player alternates per the impulse sequence; after the last
 *       [MoveUnit] command the session auto-cascades to WEAPON_ATTACK, emitting
 *       [PhaseChanged](MOVEMENT→WEAPON_ATTACK) visible via the subscriber.
 *
 * I3 – WEAPON_ATTACK.onEntry seeds the attack sequence; the first active attack player
 *       is the initiative loser (loser declares first per [calculateAttackOrder]).
 *
 * I4 – [CommitAttackImpulse] with empty declarations still advances the attack
 *       sequence impulse by impulse; [AttackDeclarationsRecorded] is emitted each
 *       time. On the final weapon impulse with an all-empty accumulated list, no
 *       [AttacksResolved] event fires, but the phase cascades to PHYSICAL_ATTACK.
 *
 * I5 – PHYSICAL_ATTACK.onEntry re-seeds the attack sequence (weapon phase consumed
 *       it). The session therefore accepts [CommitPhysicalAttackImpulse] for the
 *       loser-first player, confirming the re-seed happened.
 *
 * I6 – After the final [CommitPhysicalAttackImpulse], the session cascades through
 *       HEAT → END → INITIATIVE → MOVEMENT of turn 2. A [TurnEnded] event with
 *       turnNumber=1 is observed, followed by [InitiativeRolled] and further
 *       [PhaseChanged] events.
 *
 * I7 – In turn 2's MOVEMENT, a unit that moved in turn 1 can be moved again (i.e.,
 *       [MovementProgress.movedUnitIds] and [MovementProgress.movedInCurrentImpulse] were
 *       reset), and the [MoveUnit] command is accepted.
 */
internal class PhaseCascadeIntegrationTest {

    // Place units far apart (hex distance > 9 = medium laser range) so empty
    // attack declarations are legal throughout.
    private val p1unit = aMech("a", PlayerId.PLAYER_1, HexCoordinates(0, 0))
    private val p2unit = aMech("b", PlayerId.PLAYER_2, HexCoordinates(0, 12))

    // Scripted dice: four d6 values = two roll2d6 calls for initiative.
    // P1: (2,3)=5  P2: (4,4)=8 → P1 loses, P2 wins (non-tied, no retry needed).
    // Subsequent rolls for turn-2 initiative: (3,2)=5  (1,2)=3 → P2 loses, P1 wins.
    // HeatPhaseHandler only rolls when heat ≥ threshold (units have 0 heat here),
    // so no extra dice consumed there.
    private val roller: DiceRoller = DiceRoller.deterministic(
        // turn 1 initiative
        2, 3,   // P1 roll: 5
        4, 4,   // P2 roll: 8  → P1 loses
        // turn 2 initiative
        3, 2,   // P1 roll: 5
        1, 2,   // P2 roll: 3  → P2 loses
    )

    private fun freshSession(): BattleSession = BattleSession(
        initialGameState = GameState(UnitRoster(listOf(p1unit, p2unit)), GameMap(hexesFor(listOf(p1unit, p2unit)))),
        initialTurnState = TurnState.NULL,
        roller = roller,
    )

    // -------------------------------------------------------------------------
    // I1 – advance() fires initiative and lands at MOVEMENT
    // -------------------------------------------------------------------------

    @Test
    fun `I1 - advance fires initiative and lands at MOVEMENT with movement sequence seeded`() {
        val session = freshSession()
        val received = mutableListOf<GameEvent>()
        session.subscribe { received += it }

        val events = session.advance()

        // Phase ends at MOVEMENT
        assertThat(session.currentPhase).isEqualTo(TurnPhase.MOVEMENT)

        // InitiativeRolled was emitted
        val rolled = events.filterIsInstance<InitiativeRolled>()
        assertThat(rolled).hasSize(1)

        // PhaseChanged INITIATIVE → MOVEMENT observed
        val phaseChanges = events.filterIsInstance<PhaseChanged>()
        assertThat(phaseChanges).hasSize(1)
        assertThat(phaseChanges.single().from).isEqualTo(TurnPhase.INITIATIVE)
        assertThat(phaseChanges.single().to).isEqualTo(TurnPhase.MOVEMENT)

        // Movement sequence is seeded (non-empty)
        assertThat(session.turnState.movement.sequence.order).isNotEmpty()

        // Subscriber received the same events
        assertThat(received.filterIsInstance<InitiativeRolled>()).hasSize(1)
        assertThat(received.filterIsInstance<PhaseChanged>()).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // I2 – Movement alternates players; final move cascades to WEAPON_ATTACK
    // -------------------------------------------------------------------------

    @Test
    fun `I2 - movement alternates players and final move cascades to WEAPON_ATTACK`() {
        val session = freshSession()
        val received = mutableListOf<GameEvent>()
        session.subscribe { received += it }

        session.advance()
        received.clear() // clear advance events; focus on movement commands

        // With 1v1, initiative loser (P1) moves first, then winner (P2).
        val initiative = session.turnState.initiative
        val loser = initiative.loser  // P1 per our scripted dice
        val winner = initiative.winner // P2

        assertThat(loser).isEqualTo(PlayerId.PLAYER_1)
        assertThat(winner).isEqualTo(PlayerId.PLAYER_2)

        // Active player before any move is the loser (P1)
        assertThat(session.activePlayer).isEqualTo(loser)

        // Move the loser's unit
        val result1 = session.submitCommand(
            MoveUnit(loser, p1unit.id, stayPut(p1unit.position), MovementMode.WALK),
        )
        assertThat(result1).isInstanceOf(CommandResult.Accepted::class.java)

        // After P1 moves, active player should be P2 (winner), not yet cascaded
        assertThat(session.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(session.activePlayer).isEqualTo(winner)

        // Move the winner's unit — this is the last move; session should cascade
        val result2 = session.submitCommand(
            MoveUnit(winner, p2unit.id, stayPut(p2unit.position), MovementMode.WALK),
        )
        assertThat(result2).isInstanceOf(CommandResult.Accepted::class.java)

        val accepted2 = result2 as CommandResult.Accepted
        val phaseChanges = accepted2.events.filterIsInstance<PhaseChanged>()
        assertThat(phaseChanges).hasSize(1)
        assertThat(phaseChanges.single().from).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(phaseChanges.single().to).isEqualTo(TurnPhase.WEAPON_ATTACK)

        assertThat(session.currentPhase).isEqualTo(TurnPhase.WEAPON_ATTACK)

        // Subscriber also saw the PhaseChanged
        assertThat(received.filterIsInstance<PhaseChanged>()
            .any { it.from == TurnPhase.MOVEMENT && it.to == TurnPhase.WEAPON_ATTACK }).isTrue()
    }

    // -------------------------------------------------------------------------
    // I3 – WEAPON_ATTACK entry seeds attack sequence; loser declares first
    // -------------------------------------------------------------------------

    @Test
    fun `I3 - WEAPON_ATTACK seeds attack sequence with loser as first active player`() {
        val session = freshSession()
        session.advance()
        moveAllUnits(session)

        assertThat(session.currentPhase).isEqualTo(TurnPhase.WEAPON_ATTACK)

        val initiative = session.turnState.initiative
        val loser = initiative.loser

        // Attack sequence is seeded (non-empty)
        assertThat(session.turnState.attack.sequence.order).isNotEmpty()
        // Loser declares first
        assertThat(session.activePlayer).isEqualTo(loser)
        assertThat(session.turnState.attack.activePlayer).isEqualTo(loser)
    }

    // -------------------------------------------------------------------------
    // I4 – Empty weapon declarations advance the sequence; no AttacksResolved
    //       on empty accumulated; phase cascades to PHYSICAL_ATTACK
    // -------------------------------------------------------------------------

    @Test
    fun `I4 - empty weapon impulses emit AttackDeclarationsRecorded and cascade to PHYSICAL_ATTACK without AttacksResolved`() {
        val session = freshSession()
        val received = mutableListOf<GameEvent>()
        session.subscribe { received += it }

        session.advance()
        received.clear()
        moveAllUnits(session)
        received.clear() // focus on weapon phase

        assertThat(session.currentPhase).isEqualTo(TurnPhase.WEAPON_ATTACK)

        val initiative = session.turnState.initiative
        val loser = initiative.loser
        val winner = initiative.winner

        // 1v1 attack order: loser first, then winner (one impulse each)
        val attackOrder = session.turnState.attack.sequence.order
        assertThat(attackOrder).hasSize(2) // [Impulse(loser,1), Impulse(winner,1)]

        // Loser commits empty declarations
        val wr1 = session.submitCommand(
            CommitAttackImpulse(loser, emptyList(), emptyMap()),
        )
        assertThat(wr1).isInstanceOf(CommandResult.Accepted::class.java)
        val wrA1 = wr1 as CommandResult.Accepted
        assertThat(wrA1.events.filterIsInstance<AttackDeclarationsRecorded>()).hasSize(1)
        // No resolution yet (not the final impulse yet, even though accumulated is empty)
        assertThat(wrA1.events.filterIsInstance<AttacksResolved>()).isEmpty()
        assertThat(session.currentPhase).isEqualTo(TurnPhase.WEAPON_ATTACK)

        // Winner commits empty declarations — final impulse; accumulated is empty so no resolve
        val wr2 = session.submitCommand(
            CommitAttackImpulse(winner, emptyList(), emptyMap()),
        )
        assertThat(wr2).isInstanceOf(CommandResult.Accepted::class.java)
        val wrA2 = wr2 as CommandResult.Accepted
        assertThat(wrA2.events.filterIsInstance<AttackDeclarationsRecorded>()).hasSize(1)
        assertThat(wrA2.events.filterIsInstance<AttacksResolved>()).isEmpty()

        // Phase cascaded to PHYSICAL_ATTACK
        assertThat(session.currentPhase).isEqualTo(TurnPhase.PHYSICAL_ATTACK)
        val phaseChanges = wrA2.events.filterIsInstance<PhaseChanged>()
        assertThat(phaseChanges.any { it.from == TurnPhase.WEAPON_ATTACK && it.to == TurnPhase.PHYSICAL_ATTACK }).isTrue()

        // Subscriber also saw both AttackDeclarationsRecorded events and the PhaseChanged
        assertThat(received.filterIsInstance<AttackDeclarationsRecorded>()).hasSize(2)
        assertThat(received.filterIsInstance<AttacksResolved>()).isEmpty()
        assertThat(received.filterIsInstance<PhaseChanged>()
            .any { it.from == TurnPhase.WEAPON_ATTACK && it.to == TurnPhase.PHYSICAL_ATTACK }).isTrue()
    }

    // -------------------------------------------------------------------------
    // I5 – PHYSICAL_ATTACK.onEntry re-seeds attack sequence; loser moves first
    // -------------------------------------------------------------------------

    @Test
    fun `I5 - PHYSICAL_ATTACK re-seeds attack sequence after weapon phase consumed it`() {
        val session = freshSession()
        session.advance()
        moveAllUnits(session)
        commitWeaponImpulses(session)

        assertThat(session.currentPhase).isEqualTo(TurnPhase.PHYSICAL_ATTACK)

        val initiative = session.turnState.initiative
        val loser = initiative.loser

        // Attack sequence re-seeded: non-empty and not complete
        assertThat(session.turnState.attack.sequence.order).isNotEmpty()
        assertThat(session.turnState.attack.sequence.isComplete).isFalse()

        // Loser is first in physical attack sequence
        assertThat(session.activePlayer).isEqualTo(loser)

        // Confirm by submitting a physical impulse for the loser — it is accepted
        val pr1 = session.submitCommand(
            CommitPhysicalAttackImpulse(loser, emptyList(), emptyMap()),
        )
        assertThat(pr1).isInstanceOf(CommandResult.Accepted::class.java)
    }

    // -------------------------------------------------------------------------
    // I6 – Final physical impulse cascades HEAT → END → INITIATIVE → MOVEMENT
    //       TurnEnded(1) is emitted; turn 2 begins
    // -------------------------------------------------------------------------

    @Test
    fun `I6 - completing physical phase cascades through HEAT and END to turn 2 MOVEMENT`() {
        val session = freshSession()
        val received = mutableListOf<GameEvent>()
        session.subscribe { received += it }

        session.advance()
        received.clear()
        moveAllUnits(session)
        commitWeaponImpulses(session)
        received.clear() // focus on physical phase completion

        assertThat(session.currentPhase).isEqualTo(TurnPhase.PHYSICAL_ATTACK)

        val initiative = session.turnState.initiative
        val loser = initiative.loser
        val winner = initiative.winner

        // Submit physical impulses for both players
        val pr1 = session.submitCommand(
            CommitPhysicalAttackImpulse(loser, emptyList(), emptyMap()),
        )
        assertThat(pr1).isInstanceOf(CommandResult.Accepted::class.java)

        val pr2 = session.submitCommand(
            CommitPhysicalAttackImpulse(winner, emptyList(), emptyMap()),
        )
        assertThat(pr2).isInstanceOf(CommandResult.Accepted::class.java)

        // After the final physical impulse, the full cascade fires
        assertThat(session.currentPhase).isEqualTo(TurnPhase.MOVEMENT)

        // TurnEnded(1) was emitted and observed by the subscriber
        val turnEndedEvents = received.filterIsInstance<TurnEnded>()
        assertThat(turnEndedEvents).hasSize(1)
        assertThat(turnEndedEvents.single().turnNumber).isEqualTo(1)

        // InitiativeRolled for turn 2 was emitted
        assertThat(received.filterIsInstance<InitiativeRolled>()).hasSize(1)

        // PhaseChanged sequence: PHYSICAL_ATTACK→HEAT, HEAT→END, END→INITIATIVE, INITIATIVE→MOVEMENT
        val phaseChanges = received.filterIsInstance<PhaseChanged>()
        val changeSequence = phaseChanges.map { it.from to it.to }
        assertThat(changeSequence).containsSubsequence(
            TurnPhase.PHYSICAL_ATTACK to TurnPhase.HEAT,
            TurnPhase.HEAT to TurnPhase.END,
            TurnPhase.END to TurnPhase.INITIATIVE,
            TurnPhase.INITIATIVE to TurnPhase.MOVEMENT,
        )

        // Turn number advanced to 2
        assertThat(session.turnState.turnNumber).isEqualTo(2)
    }

    // -------------------------------------------------------------------------
    // I7 – In turn 2, units that moved in turn 1 are selectable again
    // -------------------------------------------------------------------------

    @Test
    fun `I7 - turn 2 movement state is reset so previously-moved units can be moved again`() {
        val session = freshSession()
        session.advance()
        moveAllUnits(session)
        commitWeaponImpulses(session)
        commitPhysicalImpulses(session)

        // Should now be in MOVEMENT of turn 2
        assertThat(session.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(session.turnState.turnNumber).isEqualTo(2)

        // movedUnitIds and unitsMovedInCurrentImpulse were reset by the cascade
        assertThat(session.turnState.movement.movedUnitIds).isEmpty()
        assertThat(session.turnState.movement.movedInCurrentImpulse).isEqualTo(0)

        // The active player for turn 2 can move their unit (was the turn-1 loser)
        val t2Initiative = session.turnState.initiative
        val t2Loser = t2Initiative.loser  // P2 per turn-2 scripted dice
        val loserUnit = if (t2Loser == PlayerId.PLAYER_1) p1unit else p2unit

        val moveResult = session.submitCommand(
            MoveUnit(t2Loser, loserUnit.id, stayPut(loserUnit.position), MovementMode.WALK),
        )
        assertThat(moveResult).isInstanceOf(CommandResult.Accepted::class.java)

        // Unit is now in movedUnitIds
        assertThat(session.turnState.movement.movedUnitIds).contains(loserUnit.id)
    }

    // -------------------------------------------------------------------------
    // Full single-test smoke: one whole turn via subscriber-only event collection
    // -------------------------------------------------------------------------

    @Test
    fun `full turn smoke - all cross-phase events observed by subscriber in order`() {
        val session = freshSession()
        val allEvents = mutableListOf<GameEvent>()
        session.subscribe { allEvents += it }

        // Kickstart
        session.advance()

        // Movement
        moveAllUnits(session)

        // Weapon attack
        commitWeaponImpulses(session)

        // Physical attack
        commitPhysicalImpulses(session)

        // Assert the global event sequence contains the expected milestones in order
        val phaseChanges = allEvents.filterIsInstance<PhaseChanged>().map { it.from to it.to }

        assertThat(phaseChanges).containsSubsequence(
            TurnPhase.INITIATIVE to TurnPhase.MOVEMENT,
            TurnPhase.MOVEMENT to TurnPhase.WEAPON_ATTACK,
            TurnPhase.WEAPON_ATTACK to TurnPhase.PHYSICAL_ATTACK,
            TurnPhase.PHYSICAL_ATTACK to TurnPhase.HEAT,
            TurnPhase.HEAT to TurnPhase.END,
            TurnPhase.END to TurnPhase.INITIATIVE,
            TurnPhase.INITIATIVE to TurnPhase.MOVEMENT,
        )

        // Exactly one TurnEnded(1) in the log
        val turnEndedEvents = allEvents.filterIsInstance<TurnEnded>()
        assertThat(turnEndedEvents).hasSize(1)
        assertThat(turnEndedEvents.single().turnNumber).isEqualTo(1)

        // Two InitiativeRolled events — one per turn kickstart
        assertThat(allEvents.filterIsInstance<InitiativeRolled>()).hasSize(2)

        // Landed in MOVEMENT of turn 2
        assertThat(session.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(session.turnState.turnNumber).isEqualTo(2)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Moves [unit] to the hex immediately north of its current position
     * (same position if staying in place isn't acceptable — we use a single
     * adjacent step that costs 1 MP so the move is always legal for a walking
     * mech). For simplicity we use the unit's current position as both start
     * and end (zero-hex-entered, valid "stationary" move: 0 MP spent).
     */
    private fun stayPut(position: HexCoordinates): ReachableHex = ReachableHex(
        position = position,
        facing = HexDirection.N,
        mpSpent = 0,
        path = listOf(MovementStep(position, HexDirection.N)),
    )

    /** Move both units in initiative order (one impulse each in 1v1). */
    private fun moveAllUnits(session: BattleSession) {
        val loser = session.turnState.initiative.loser
        val winner = session.turnState.initiative.winner
        val loserUnit = if (loser == PlayerId.PLAYER_1) p1unit else p2unit
        val winnerUnit = if (winner == PlayerId.PLAYER_1) p1unit else p2unit

        val r1 = session.submitCommand(
            MoveUnit(loser, loserUnit.id, stayPut(loserUnit.position), MovementMode.WALK),
        )
        check(r1 is CommandResult.Accepted) { "move failed: $r1" }

        val r2 = session.submitCommand(
            MoveUnit(winner, winnerUnit.id, stayPut(winnerUnit.position), MovementMode.WALK),
        )
        check(r2 is CommandResult.Accepted) { "move failed: $r2" }
    }

    /** Commit two empty weapon impulses in attack order (loser first, then winner). */
    private fun commitWeaponImpulses(session: BattleSession) {
        val loser = session.turnState.initiative.loser
        val winner = session.turnState.initiative.winner

        val r1 = session.submitCommand(CommitAttackImpulse(loser, emptyList(), emptyMap()))
        check(r1 is CommandResult.Accepted) { "weapon impulse 1 failed: $r1" }

        val r2 = session.submitCommand(CommitAttackImpulse(winner, emptyList(), emptyMap()))
        check(r2 is CommandResult.Accepted) { "weapon impulse 2 failed: $r2" }
    }

    /** Commit two empty physical impulses in attack order (loser first, then winner). */
    private fun commitPhysicalImpulses(session: BattleSession) {
        val loser = session.turnState.initiative.loser
        val winner = session.turnState.initiative.winner

        val r1 = session.submitCommand(CommitPhysicalAttackImpulse(loser, emptyList(), emptyMap()))
        check(r1 is CommandResult.Accepted) { "physical impulse 1 failed: $r1" }

        val r2 = session.submitCommand(CommitPhysicalAttackImpulse(winner, emptyList(), emptyMap()))
        check(r2 is CommandResult.Accepted) { "physical impulse 2 failed: $r2" }
    }

}
