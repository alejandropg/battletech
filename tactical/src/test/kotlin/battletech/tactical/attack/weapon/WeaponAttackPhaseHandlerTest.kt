package battletech.tactical.attack.weapon

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.mediumLaser
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttackProgress
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.CommitAttackImpulse
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachableHex
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class WeaponAttackPhaseHandlerTest {

    private val handler = WeaponAttackPhaseHandler()

    // PLAYER_1 is the initiative loser (declares first), PLAYER_2 is the winner.
    private val initiative = Initiative(
        rolls = emptyMap(),
        loser = PlayerId.PLAYER_1,
        winner = PlayerId.PLAYER_2,
    )

    private val attacker = aUnit(
        id = "attacker",
        owner = PlayerId.PLAYER_1,
        weapons = listOf(mediumLaser()),
        position = HexCoordinates(0, 0),
    )
    private val target = aUnit(
        id = "target",
        owner = PlayerId.PLAYER_2,
        weapons = listOf(mediumLaser()),
        position = HexCoordinates(1, 0),
    )
    private val gameState = aGameState(units = listOf(attacker, target))

    // A seeded two-impulse sequence: PLAYER_1 first, then PLAYER_2.
    private fun seededTwoImpulseTurn(): TurnState = TurnState(
        initiative = initiative,
        attack = AttackProgress(
            sequence = ImpulseSequence(
                order = listOf(
                    Impulse(PlayerId.PLAYER_1, 1),
                    Impulse(PlayerId.PLAYER_2, 1),
                ),
            ),
        ),
    )

    // A seeded one-impulse sequence (single active impulse, not yet complete).
    private fun seededOneImpulseTurn(): TurnState = TurnState(
        initiative = initiative,
        attack = AttackProgress(
            sequence = ImpulseSequence(
                order = listOf(Impulse(PlayerId.PLAYER_1, 1)),
            ),
        ),
    )

    // A completed sequence (currentIndex past end).
    private fun completedSequenceTurn(): TurnState = TurnState(
        initiative = initiative,
        attack = AttackProgress(
            sequence = ImpulseSequence(
                order = listOf(Impulse(PlayerId.PLAYER_1, 1)),
                currentIndex = 1,
            ),
        ),
    )

    // An empty sequence (no order).
    private fun emptySequenceTurn(): TurnState = TurnState(
        initiative = initiative,
        attack = AttackProgress(sequence = ImpulseSequence(order = emptyList())),
    )

    private val noRoller = DiceRoller.deterministic(emptyList())

    // ── accepts ──────────────────────────────────────────────────────────────

    @Test
    fun `accepts CommitAttackImpulse when sequence is seeded and not complete`() {
        val cmd = CommitAttackImpulse(PlayerId.PLAYER_1, emptyList(), emptyMap())
        assertThat(handler.accepts(cmd, seededOneImpulseTurn())).isTrue()
    }

    @Test
    fun `accepts returns false for a different command type`() {
        val cmd = MoveUnit(
            playerId = PlayerId.PLAYER_1,
            unitId = attacker.id,
            destination = ReachableHex(
                position = HexCoordinates(1, 0),
                facing = HexDirection.N,
                mpSpent = 1,
                path = emptyList(),
            ),
            mode = MovementMode.WALK,
        )
        assertThat(handler.accepts(cmd, seededOneImpulseTurn())).isFalse()
    }

    @Test
    fun `accepts returns false when sequence is empty`() {
        val cmd = CommitAttackImpulse(PlayerId.PLAYER_1, emptyList(), emptyMap())
        assertThat(handler.accepts(cmd, emptySequenceTurn())).isFalse()
    }

    @Test
    fun `accepts returns false when sequence is complete`() {
        val cmd = CommitAttackImpulse(PlayerId.PLAYER_1, emptyList(), emptyMap())
        assertThat(handler.accepts(cmd, completedSequenceTurn())).isFalse()
    }

    // ── activePlayer ─────────────────────────────────────────────────────────

    @Test
    fun `activePlayer returns null when sequence is empty`() {
        assertThat(handler.activePlayer(emptySequenceTurn())).isNull()
    }

    @Test
    fun `activePlayer returns null when sequence is complete`() {
        assertThat(handler.activePlayer(completedSequenceTurn())).isNull()
    }

    @Test
    fun `activePlayer returns the sequence active player when sequence is in progress`() {
        assertThat(handler.activePlayer(seededOneImpulseTurn())).isEqualTo(PlayerId.PLAYER_1)
    }

    // ── isComplete ───────────────────────────────────────────────────────────

    @Test
    fun `isComplete returns false when sequence order is empty`() {
        assertThat(handler.isComplete(emptySequenceTurn())).isFalse()
    }

    @Test
    fun `isComplete returns false when sequence is in progress mid-sequence`() {
        assertThat(handler.isComplete(seededTwoImpulseTurn())).isFalse()
    }

    @Test
    fun `isComplete returns true when sequence is seeded and complete`() {
        assertThat(handler.isComplete(completedSequenceTurn())).isTrue()
    }

    // ── onEntry ──────────────────────────────────────────────────────────────

    @Test
    fun `onEntry seeds attackSequence from initiative and active unit counts when empty`() {
        val turn = emptySequenceTurn()
        val outcome = handler.onEntry(gameState, turn, noRoller)

        // PLAYER_1 is loser (1 active unit) → first impulse; PLAYER_2 winner (1 active unit) → second.
        assertThat(outcome.turn.attack.sequence.order).containsExactly(
            Impulse(PlayerId.PLAYER_1, 1),
            Impulse(PlayerId.PLAYER_2, 1),
        )
        assertThat(outcome.turn.attack.sequence.currentIndex).isEqualTo(0)
    }

    @Test
    fun `onEntry does not re-seed when a sequence is already in progress`() {
        val turn = seededTwoImpulseTurn()
        val outcome = handler.onEntry(gameState, turn, noRoller)

        // Turn must be returned unchanged — same sequence reference / contents.
        assertThat(outcome.turn).isEqualTo(turn)
        assertThat(outcome.events).isEmpty()
    }

    @Test
    fun `onEntry re-seeds when the existing sequence is complete`() {
        val turn = completedSequenceTurn()
        val outcome = handler.onEntry(gameState, turn, noRoller)

        // A completed sequence (guard at lines 90-92) must be replaced with a fresh one.
        assertThat(outcome.turn.attack.sequence.currentIndex).isEqualTo(0)
        assertThat(outcome.turn.attack.sequence.order).isNotEmpty()
        assertThat(outcome.turn.attack.sequence.isComplete).isFalse()
    }

    // ── apply mid-impulse ────────────────────────────────────────────────────

    @Test
    fun `apply mid-impulse accumulates declarations and advances sequence without resolving`() {
        val declaration = AttackDeclaration(
            attackerId = attacker.id,
            targetId = target.id,
            weaponIndex = 0,
            isPrimary = true,
        )
        val cmd = CommitAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(declaration),
            torsoFacings = emptyMap(),
        )
        // Two-impulse turn — after this apply we're on impulse index 1 (not yet complete).
        val turn = seededTwoImpulseTurn()
        val outcome = handler.apply(cmd, gameState, turn, noRoller)

        assertThat(outcome.turn.attack.weaponDeclarations).containsExactly(declaration)
        assertThat(outcome.turn.attack.sequence.currentIndex).isEqualTo(1)
        assertThat(outcome.events.filterIsInstance<AttackDeclarationsRecorded>()).hasSize(1)
        assertThat(outcome.events.filterIsInstance<AttacksResolved>()).isEmpty()
    }

    // ── apply final impulse ──────────────────────────────────────────────────

    @Test
    fun `apply on final impulse resolves accumulated declarations and emits AttacksResolved`() {
        val declaration = AttackDeclaration(
            attackerId = attacker.id,
            targetId = target.id,
            weaponIndex = 0,
            isPrimary = true,
        )
        // Pre-accumulate the PLAYER_1 declaration from the first impulse.
        val turn = seededTwoImpulseTurn().copy(
            attack = AttackProgress(
                sequence = ImpulseSequence(
                    order = listOf(
                        Impulse(PlayerId.PLAYER_1, 1),
                        Impulse(PlayerId.PLAYER_2, 1),
                    ),
                    currentIndex = 1, // PLAYER_2's impulse is next (final)
                ),
                weaponDeclarations = listOf(declaration),
            ),
        )
        val cmd = CommitAttackImpulse(
            playerId = PlayerId.PLAYER_2,
            declarations = emptyList(),
            torsoFacings = emptyMap(),
        )
        // to-hit: 4+4=8 >= 4 (hit); location: 3+4=7 → CENTER_TORSO
        val roller = DiceRoller.deterministic(4, 4, 3, 4)

        val outcome = handler.apply(cmd, gameState, turn, roller)

        assertThat(outcome.turn.attack.weaponDeclarations).isEmpty()
        val resolved = outcome.events.filterIsInstance<AttacksResolved>().single()
        assertThat(resolved.results).hasSize(1)
        assertThat(resolved.results.single().hit).isTrue()
    }

    // ── torso facings ────────────────────────────────────────────────────────

    @Test
    fun `apply emits TorsoFacingsApplied and updates state when torsoFacings non-empty`() {
        val newFacing = HexDirection.NE
        val cmd = CommitAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = emptyList(),
            torsoFacings = mapOf(attacker.id to newFacing),
        )
        val outcome = handler.apply(cmd, gameState, seededTwoImpulseTurn(), noRoller)

        val event = outcome.events.filterIsInstance<TorsoFacingsApplied>().single()
        assertThat(event.facings).containsEntry(attacker.id, newFacing)
        assertThat(outcome.state.unitById(attacker.id)!!.torsoFacing).isEqualTo(newFacing)
    }

    @Test
    fun `apply does not emit TorsoFacingsApplied when torsoFacings empty`() {
        val cmd = CommitAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = emptyList(),
            torsoFacings = emptyMap(),
        )
        val outcome = handler.apply(cmd, gameState, seededTwoImpulseTurn(), noRoller)

        assertThat(outcome.events.filterIsInstance<TorsoFacingsApplied>()).isEmpty()
    }
}
