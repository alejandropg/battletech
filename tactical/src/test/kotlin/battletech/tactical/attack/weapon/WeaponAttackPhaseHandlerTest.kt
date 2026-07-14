package battletech.tactical.attack.weapon

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.AttackResult
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.Terrain
import battletech.tactical.movement.ReachableHex
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.aWeapon
import battletech.tactical.query.anArmorLayout
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.query.mediumLaser
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttackProgress
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommitAttackImpulse
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.RuleRejection
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitFell
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.destructionReason
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
        assertThat(resolved.results.single()).isInstanceOf(AttackResult.Hit::class.java)
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
        assertThat(outcome.state.unitById(attacker.id).torsoFacing).isEqualTo(newFacing)
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

    @Test
    fun `apply does not emit TorsoFacingsApplied for a declared facing equal to the unit's current facing`() {
        // attacker's torsoFacing defaults to its facing (N) — declaring "N" again is a no-op,
        // not a twist, and must not be echoed back as a change.
        val cmd = CommitAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = emptyList(),
            torsoFacings = mapOf(attacker.id to attacker.torsoFacing),
        )
        val outcome = handler.apply(cmd, gameState, seededTwoImpulseTurn(), noRoller)

        assertThat(outcome.events.filterIsInstance<TorsoFacingsApplied>()).isEmpty()
    }

    // ── gyro 1st-crit immediate PSR ─────────────────────────────────────────────

    // Thin CT (0 armor, 1 IS) so a medium laser hit (5 dmg) blows into IS and
    // triggers exactly one crit check; pilotingSkill 5 -> gyro-modified PSR TN 8.
    private val thinCtTarget = aUnit(
        id = "target",
        owner = PlayerId.PLAYER_2,
        weapons = listOf(mediumLaser()),
        position = HexCoordinates(1, 0),
        facing = FiringArc.bearingDirection(HexCoordinates(1, 0), HexCoordinates(0, 0)),
        armor = anArmorLayout(centerTorso = 0, centerTorsoRear = 0),
        internalStructure = anInternalStructureLayout(centerTorso = 1),
    )
    private val thinCtGameState = aGameState(units = listOf(attacker, thinCtTarget))

    private fun thinCtTurn(declaration: AttackDeclaration): TurnState = seededTwoImpulseTurn().copy(
        attack = AttackProgress(
            sequence = ImpulseSequence(
                order = listOf(
                    Impulse(PlayerId.PLAYER_1, 1),
                    Impulse(PlayerId.PLAYER_2, 1),
                ),
                currentIndex = 1,
            ),
            weaponDeclarations = listOf(declaration),
        ),
    )

    @Test
    fun `a unit taking its first gyro crit fails its PSR and falls`() {
        val declaration = AttackDeclaration(attacker.id, thinCtTarget.id, 0, true)
        val cmd = CommitAttackImpulse(PlayerId.PLAYER_2, emptyList(), emptyMap())
        // to-hit 4+4=8 (hit, TN 4); location 3+4=7 (CENTER_TORSO).
        // Medium laser 5 dmg into 0-armor/1-IS CT: 1 structure damage, 4 excess
        // transfers — but CENTER_TORSO doesn't transfer, so excess is dropped; one
        // crit check fires on CENTER_TORSO. Crit roll 4+5=9 -> 1 crit; block 1
        // (upper), slot 4 -> index 3 (Gyro, 1st crit).
        // Gyro PSR (TN 5+3=8): roll 2+3=5 -> fails -> fall (loc 2+3=5, facing d6=1).
        // pilot hit 1 consciousness 2d6 (3,3 → 6 ≥ target 3 → conscious).
        val roller = DiceRoller.deterministic(4, 4, 3, 4, 4, 5, 1, 4, 2, 3, 2, 3, 1, 3, 3)

        val outcome = handler.apply(cmd, thinCtGameState, thinCtTurn(declaration), roller)

        val updatedTarget = outcome.state.unitById(thinCtTarget.id)
        assertThat(updatedTarget.criticalHits[MechLocation.CENTER_TORSO]).containsExactly(3)
        assertThat(updatedTarget.isProne).isTrue()
        val fell = outcome.events.filterIsInstance<UnitFell>().single()
        assertThat(fell.unitId).isEqualTo(thinCtTarget.id)
    }

    @Test
    fun `a unit taking its first gyro crit passes its PSR and stays up`() {
        val declaration = AttackDeclaration(attacker.id, thinCtTarget.id, 0, true)
        val cmd = CommitAttackImpulse(PlayerId.PLAYER_2, emptyList(), emptyMap())
        // Same crit setup as above, but the gyro PSR roll succeeds: 4+4=8 >= TN 8.
        val roller = DiceRoller.deterministic(4, 4, 3, 4, 4, 5, 1, 4, 4, 4)

        val outcome = handler.apply(cmd, thinCtGameState, thinCtTurn(declaration), roller)

        val updatedTarget = outcome.state.unitById(thinCtTarget.id)
        assertThat(updatedTarget.criticalHits[MechLocation.CENTER_TORSO]).containsExactly(3)
        assertThat(updatedTarget.isProne).isFalse()
        assertThat(outcome.events.filterIsInstance<UnitFell>()).isEmpty()
    }

    // ── gyro 2nd crit: automatic crash, NOT elimination (rules doc §3) ───────────

    // ── validate ─────────────────────────────────────────────────────────────

    /** A CommitAttackImpulse that the session would have already accepted (correct player, seeded turn). */
    private fun validCmd(
        declarations: List<AttackDeclaration> = emptyList(),
        torsoFacings: Map<UnitId, HexDirection> = emptyMap(),
    ) = CommitAttackImpulse(
        playerId = PlayerId.PLAYER_1,
        declarations = declarations,
        torsoFacings = torsoFacings,
    )

    @Test
    fun `validate returns null for an empty volley`() {
        assertThat(handler.validate(validCmd(), gameState, seededOneImpulseTurn())).isNull()
    }

    @Test
    fun `validate returns null for a fully legal weapon declaration`() {
        // attacker (PLAYER_1) at (0,0) firing weapon 0 at target (PLAYER_2) at (1,0) — in range.
        val decl = AttackDeclaration(attacker.id, target.id, weaponIndex = 0, isPrimary = true)
        assertThat(handler.validate(validCmd(declarations = listOf(decl)), gameState, seededOneImpulseTurn())).isNull()
    }

    @Test
    fun `validate rejects attacker owned by different player`() {
        // target is owned by PLAYER_2; the command is from PLAYER_1 trying to command it.
        val decl = AttackDeclaration(target.id, attacker.id, weaponIndex = 0, isPrimary = true)
        val result = handler.validate(validCmd(declarations = listOf(decl)), gameState, seededOneImpulseTurn())
        assertThat(result).isEqualTo(
            CommandRejection.NotYourUnit(unitId = target.id, owner = PlayerId.PLAYER_2, attemptedBy = PlayerId.PLAYER_1),
        )
    }

    @Test
    fun `validate rejects out-of-bounds weapon index`() {
        val decl = AttackDeclaration(attacker.id, target.id, weaponIndex = 99, isPrimary = true)
        val result = handler.validate(validCmd(declarations = listOf(decl)), gameState, seededOneImpulseTurn())
        assertThat(result).isEqualTo(CommandRejection.NoSuchWeapon(attacker.id, 99))
    }

    @Test
    fun `validate rejects friendly-fire target`() {
        // Add a second PLAYER_1 unit; attempt to declare it as a target.
        val ally = aUnit(id = "ally", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 0))
        val state = aGameState(units = listOf(attacker, ally, target))
        val decl = AttackDeclaration(attacker.id, ally.id, weaponIndex = 0, isPrimary = true)
        val result = handler.validate(validCmd(declarations = listOf(decl)), state, seededOneImpulseTurn())
        assertThat(result).isEqualTo(CommandRejection.FriendlyFire(ally.id))
    }

    @Test
    fun `validate rejects destroyed target`() {
        val destroyedEnemy = aUnit(id = "dead", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 0), isDestroyed = true)
        val state = aGameState(units = listOf(attacker, destroyedEnemy))
        val decl = AttackDeclaration(attacker.id, destroyedEnemy.id, weaponIndex = 0, isPrimary = true)
        val result = handler.validate(validCmd(declarations = listOf(decl)), state, seededOneImpulseTurn())
        assertThat(result).isEqualTo(CommandRejection.RuleViolation(RuleRejection.TargetDestroyed))
    }

    @Test
    fun `validate rejects out-of-range weapon via RuleViolation`() {
        // target placed 15 hexes away — beyond medium laser long range (9).
        val farTarget = aUnit(id = "far", owner = PlayerId.PLAYER_2, position = HexCoordinates(15, 0))
        val state = aGameState(units = listOf(attacker, farTarget))
        val decl = AttackDeclaration(attacker.id, farTarget.id, weaponIndex = 0, isPrimary = true)
        val result = handler.validate(validCmd(declarations = listOf(decl)), state, seededOneImpulseTurn())
        assertThat(result).isInstanceOf(CommandRejection.RuleViolation::class.java)
        assertThat((result as CommandRejection.RuleViolation).rule).isInstanceOf(RuleRejection.OutOfRange::class.java)
    }

    @Test
    fun `validate rejects blocked LOS via RuleViolation`() {
        // Heavy woods at (0,-1) and light woods at (0,-2) between attacker (0,0) and enemy (0,-3).
        val blockedEnemy = aUnit(id = "blocked", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, -3))
        val hexes = mapOf(
            HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0), Terrain.CLEAR),
            HexCoordinates(0, -1) to Hex(HexCoordinates(0, -1), Terrain.HEAVY_WOODS),
            HexCoordinates(0, -2) to Hex(HexCoordinates(0, -2), Terrain.LIGHT_WOODS),
            HexCoordinates(0, -3) to Hex(HexCoordinates(0, -3), Terrain.CLEAR),
        )
        val state = aGameState(units = listOf(attacker, blockedEnemy), hexes = hexes)
        val decl = AttackDeclaration(attacker.id, blockedEnemy.id, weaponIndex = 0, isPrimary = true)
        val result = handler.validate(validCmd(declarations = listOf(decl)), state, seededOneImpulseTurn())
        assertThat(result).isInstanceOf(CommandRejection.RuleViolation::class.java)
        assertThat((result as CommandRejection.RuleViolation).rule).isInstanceOf(RuleRejection.NoLineOfSight::class.java)
    }

    @Test
    fun `validate rejects critically-destroyed weapon via RuleViolation`() {
        val destroyedWeaponAttacker = aUnit(
            id = "attacker",
            owner = PlayerId.PLAYER_1,
            weapons = listOf(aWeapon(name = "Broken Laser", destroyed = true)),
            position = HexCoordinates(0, 0),
        )
        val state = aGameState(units = listOf(destroyedWeaponAttacker, target))
        val decl = AttackDeclaration(destroyedWeaponAttacker.id, target.id, weaponIndex = 0, isPrimary = true)
        val result = handler.validate(validCmd(declarations = listOf(decl)), state, seededOneImpulseTurn())
        assertThat(result).isInstanceOf(CommandRejection.RuleViolation::class.java)
        assertThat((result as CommandRejection.RuleViolation).rule).isInstanceOf(RuleRejection.WeaponDestroyed::class.java)
    }

    @Test
    fun `validate rejects torso-facing unit owned by different player`() {
        // target is PLAYER_2; command is from PLAYER_1.
        val result = handler.validate(
            validCmd(torsoFacings = mapOf(target.id to HexDirection.NE)),
            gameState,
            seededOneImpulseTurn(),
        )
        assertThat(result).isEqualTo(
            CommandRejection.NotYourUnit(unitId = target.id, owner = PlayerId.PLAYER_2, attemptedBy = PlayerId.PLAYER_1),
        )
    }

    @Test
    fun `validate rejects torso twist more than one step from leg facing`() {
        // attacker has leg facing N (ordinal 0); S (ordinal 3) is 3 steps away → illegal.
        val result = handler.validate(
            validCmd(torsoFacings = mapOf(attacker.id to HexDirection.S)),
            gameState,
            seededOneImpulseTurn(),
        )
        assertThat(result).isEqualTo(CommandRejection.IllegalTorsoTwist(attacker.id, HexDirection.S))
    }

    @Test
    fun `validate accepts a legal one-step torso twist`() {
        // NE is 1 step clockwise from N — legal.
        val result = handler.validate(
            validCmd(torsoFacings = mapOf(attacker.id to HexDirection.NE)),
            gameState,
            seededOneImpulseTurn(),
        )
        assertThat(result).isNull()
    }

    @Test
    fun `validate accepts a torso facing equal to leg facing (no-op twist)`() {
        val result = handler.validate(
            validCmd(torsoFacings = mapOf(attacker.id to HexDirection.N)),
            gameState,
            seededOneImpulseTurn(),
        )
        assertThat(result).isNull()
    }

    @Test
    fun `a unit taking its second gyro crit crashes automatically without a PSR and is not eliminated`() {
        // Target already has its 1st gyro crit (CT slot index 3). CT armor 0, IS 10 so a
        // 5-dmg laser deals structure damage (firing a crit check) WITHOUT zeroing CT IS.
        val target = aUnit(
            id = "target",
            owner = PlayerId.PLAYER_2,
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(1, 0),
            facing = FiringArc.bearingDirection(HexCoordinates(1, 0), HexCoordinates(0, 0)),
            armor = anArmorLayout(centerTorso = 0, centerTorsoRear = 0),
            internalStructure = anInternalStructureLayout(centerTorso = 10),
        ).copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(3)))
        val state = aGameState(units = listOf(attacker, target))
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)
        val cmd = CommitAttackImpulse(PlayerId.PLAYER_2, emptyList(), emptyMap())
        // to-hit 4+4=8 (hit); location 3+4=7 (CENTER_TORSO); 5 dmg -> 5 structure damage
        // (IS 10->5), one crit check: 4+5=9 -> 1 crit; block 1 (upper), slot 5 -> index 4
        // (Gyro, 2nd crit). gyro crits 1 -> 2 => shattered: automatic crash, NO PSR roll.
        // fall: location 2+3=5, facing d6=1.
        // pilot hit 1 consciousness 2d6 (3,3 → 6 ≥ target 3 → conscious).
        val roller = DiceRoller.deterministic(4, 4, 3, 4, 4, 5, 1, 5, 2, 3, 1, 3, 3)

        val outcome = handler.apply(cmd, state, thinCtTurn(declaration), roller)

        val updatedTarget = outcome.state.unitById(target.id)
        assertThat(updatedTarget.criticalHits[MechLocation.CENTER_TORSO]).containsExactlyInAnyOrder(3, 4)
        assertThat(updatedTarget.isProne).isTrue()
        assertThat(updatedTarget.isDestroyed).isFalse()
        assertThat(destructionReason(updatedTarget)).isNull()
        assertThat(outcome.events.filterIsInstance<UnitFell>().single().unitId).isEqualTo(target.id)
    }
}
