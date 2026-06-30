package battletech.tactical.attack.physical

import battletech.tactical.attack.physical.PhysicalAttackKind.Kick
import battletech.tactical.attack.physical.PhysicalAttackKind.Punch
import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.session.AttackProgress
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommitPhysicalAttackImpulse
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.PhysicalAttacksResolved
import battletech.tactical.session.RuleRejection
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitFell
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PhysicalAttackPhaseHandlerTest {

    private val handler = PhysicalAttackPhaseHandler()

    private val attackerPos = HexCoordinates(0, 0)
    private val targetPos = HexCoordinates(1, 0)
    private val bearing = FiringArc.bearingDirection(targetPos, attackerPos)

    private val attacker = aUnit(id = "attacker", tonnage = 50, pilotingSkill = 5, position = attackerPos)
    private val target = aUnit(id = "target", owner = PlayerId.PLAYER_2, position = targetPos, facing = bearing)
    private val gameState = aGameState(units = listOf(attacker, target))

    private fun turnWithOneImpulse() = TurnState(
        initiative = Initiative(emptyMap(), PlayerId.PLAYER_1, PlayerId.PLAYER_2),
        attack = AttackProgress(sequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1)))),
    )

    @Test
    fun `committing a punch resolves it and applies damage on the final impulse`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT))),
            torsoFacings = emptyMap(),
        )
        // hit (3+3=6 >= 5), location die 1 -> Left Arm on the front column.
        val roller = DiceRoller.deterministic(3, 3, 1)

        val outcome = handler.apply(command, gameState, turnWithOneImpulse(), roller)

        val resolved = outcome.events.filterIsInstance<PhysicalAttacksResolved>().single()
        assertThat(resolved.results.single().hit).isTrue()
        assertThat(outcome.state.unitById(target.id)!!.armor.leftArm)
            .isEqualTo(target.armor.leftArm - 5)
        assertThat(outcome.turn.attack.physicalDeclarations).isEmpty()
    }

    @Test
    fun `a kick knockdown emits a UnitFell event`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, target.id, Kick(Side.RIGHT))),
            torsoFacings = emptyMap(),
        )
        // hit (2+2, TN 3); location 1; target PSR 1+1 fail; fall 3+4 -> CT; facing 1;
        // pilot hit 1 consciousness 2d6 (3,3 → 6 ≥ target 3 → conscious).
        val roller = DiceRoller.deterministic(2, 2, 1, 1, 1, 3, 4, 1, 3, 3)

        val outcome = handler.apply(command, gameState, turnWithOneImpulse(), roller)

        val fell = outcome.events.filterIsInstance<UnitFell>().single()
        assertThat(fell.unitId).isEqualTo(target.id)
        assertThat(outcome.state.unitById(target.id)!!.isProne).isTrue()
    }

    @Test
    fun `validate rejects a unit that both punches and kicks`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(
                PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT)),
                PhysicalAttackDeclaration(attacker.id, target.id, Kick(Side.RIGHT)),
            ),
            torsoFacings = emptyMap(),
        )

        val rejection = handler.validate(command, gameState, turnWithOneImpulse())

        assertThat(rejection)
            .isInstanceOf(CommandRejection.RuleViolation::class.java)
        val rule = (rejection as CommandRejection.RuleViolation).rule
        assertThat(rule).isInstanceOf(RuleRejection.PunchAndKickSameTurn::class.java)
    }

    // ── validate: per-declaration checks ─────────────────────────────────────

    @Test
    fun `validate accepts a legal single punch declaration`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT))),
            torsoFacings = emptyMap(),
        )
        assertThat(handler.validate(command, gameState, turnWithOneImpulse())).isNull()
    }

    @Test
    fun `validate accepts a legal single kick declaration`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, target.id, Kick(Side.RIGHT))),
            torsoFacings = emptyMap(),
        )
        assertThat(handler.validate(command, gameState, turnWithOneImpulse())).isNull()
    }

    @Test
    fun `validate rejects unknown attacker`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(UnitId("ghost"), target.id, Punch(Side.LEFT))),
            torsoFacings = emptyMap(),
        )
        assertThat(handler.validate(command, gameState, turnWithOneImpulse()))
            .isEqualTo(CommandRejection.UnknownUnit(UnitId("ghost")))
    }

    @Test
    fun `validate rejects attacker owned by different player`() {
        // target is PLAYER_2; command is from PLAYER_1 trying to command it
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(target.id, attacker.id, Punch(Side.LEFT))),
            torsoFacings = emptyMap(),
        )
        assertThat(handler.validate(command, gameState, turnWithOneImpulse()))
            .isEqualTo(
                CommandRejection.NotYourTurn(
                    activePlayer = PlayerId.PLAYER_2,
                    attemptedBy = PlayerId.PLAYER_1,
                ),
            )
    }

    @Test
    fun `validate rejects unknown target`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, UnitId("ghost"), Punch(Side.LEFT))),
            torsoFacings = emptyMap(),
        )
        assertThat(handler.validate(command, gameState, turnWithOneImpulse()))
            .isEqualTo(CommandRejection.UnknownUnit(UnitId("ghost")))
    }

    @Test
    fun `validate rejects friendly fire target`() {
        val ally = aUnit(id = "ally", owner = PlayerId.PLAYER_1, position = targetPos)
        val state = aGameState(units = listOf(attacker, ally, target))
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, ally.id, Punch(Side.LEFT))),
            torsoFacings = emptyMap(),
        )
        assertThat(handler.validate(command, state, turnWithOneImpulse()))
            .isEqualTo(CommandRejection.FriendlyFire(ally.id))
    }

    @Test
    fun `validate rejects destroyed target`() {
        val deadTarget = aUnit(id = "dead", owner = PlayerId.PLAYER_2, position = targetPos, isDestroyed = true)
        val state = aGameState(units = listOf(attacker, deadTarget))
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, deadTarget.id, Punch(Side.LEFT))),
            torsoFacings = emptyMap(),
        )
        assertThat(handler.validate(command, state, turnWithOneImpulse()))
            .isEqualTo(CommandRejection.RuleViolation(RuleRejection.TargetDestroyed))
    }

    @Test
    fun `validate rejects punch to non-adjacent target via RuleViolation`() {
        val farTarget = aUnit(id = "far", owner = PlayerId.PLAYER_2, position = HexCoordinates(5, 0))
        val state = aGameState(units = listOf(attacker, farTarget))
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, farTarget.id, Punch(Side.LEFT))),
            torsoFacings = emptyMap(),
        )
        val result = handler.validate(command, state, turnWithOneImpulse())
        assertThat(result).isInstanceOf(CommandRejection.RuleViolation::class.java)
        assertThat((result as CommandRejection.RuleViolation).rule)
            .isInstanceOf(RuleRejection.NotAdjacent::class.java)
    }

    @Test
    fun `validate rejects kick to non-adjacent target via RuleViolation`() {
        val farTarget = aUnit(id = "far", owner = PlayerId.PLAYER_2, position = HexCoordinates(5, 0))
        val state = aGameState(units = listOf(attacker, farTarget))
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, farTarget.id, Kick(Side.RIGHT))),
            torsoFacings = emptyMap(),
        )
        val result = handler.validate(command, state, turnWithOneImpulse())
        assertThat(result).isInstanceOf(CommandRejection.RuleViolation::class.java)
        assertThat((result as CommandRejection.RuleViolation).rule)
            .isInstanceOf(RuleRejection.NotAdjacent::class.java)
    }

    @Test
    fun `validate rejects punch by prone attacker via RuleViolation`() {
        val proneAttacker = attacker.copy(isProne = true)
        val state = aGameState(units = listOf(proneAttacker, target))
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(proneAttacker.id, target.id, Punch(Side.LEFT))),
            torsoFacings = emptyMap(),
        )
        val result = handler.validate(command, state, turnWithOneImpulse())
        assertThat(result).isInstanceOf(CommandRejection.RuleViolation::class.java)
        assertThat((result as CommandRejection.RuleViolation).rule)
            .isEqualTo(RuleRejection.AttackerProne)
    }

    @Test
    fun `validate rejects kick after running via RuleViolation`() {
        val runningAttacker = attacker.copy(movementThisTurn = MovementThisTurn(MovementMode.RUN, 3))
        val state = aGameState(units = listOf(runningAttacker, target))
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(runningAttacker.id, target.id, Kick(Side.RIGHT))),
            torsoFacings = emptyMap(),
        )
        val result = handler.validate(command, state, turnWithOneImpulse())
        assertThat(result).isInstanceOf(CommandRejection.RuleViolation::class.java)
        assertThat((result as CommandRejection.RuleViolation).rule)
            .isEqualTo(RuleRejection.CannotKickAfterRunningOrJumping)
    }

    // ── validate: torso facings ───────────────────────────────────────────────

    @Test
    fun `validate rejects unknown unit in torso facings`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = emptyList(),
            torsoFacings = mapOf(UnitId("ghost") to HexDirection.NE),
        )
        assertThat(handler.validate(command, gameState, turnWithOneImpulse()))
            .isEqualTo(CommandRejection.UnknownUnit(UnitId("ghost")))
    }

    @Test
    fun `validate rejects torso-facing unit owned by different player`() {
        // target is PLAYER_2; command is from PLAYER_1
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = emptyList(),
            torsoFacings = mapOf(target.id to HexDirection.NE),
        )
        assertThat(handler.validate(command, gameState, turnWithOneImpulse()))
            .isEqualTo(
                CommandRejection.NotYourTurn(
                    activePlayer = PlayerId.PLAYER_2,
                    attemptedBy = PlayerId.PLAYER_1,
                ),
            )
    }

    @Test
    fun `validate rejects torso twist more than one step from leg facing`() {
        // attacker faces N (ordinal 0); S (ordinal 3) is 3 steps away — illegal
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = emptyList(),
            torsoFacings = mapOf(attacker.id to HexDirection.S),
        )
        assertThat(handler.validate(command, gameState, turnWithOneImpulse()))
            .isEqualTo(CommandRejection.IllegalTorsoTwist(attacker.id, HexDirection.S))
    }

    @Test
    fun `validate accepts a legal one-step torso twist`() {
        // NE is 1 step clockwise from N — legal
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = emptyList(),
            torsoFacings = mapOf(attacker.id to HexDirection.NE),
        )
        assertThat(handler.validate(command, gameState, turnWithOneImpulse())).isNull()
    }

    // ── validate: limb-limit rejections still work ────────────────────────────

    @Test
    fun `validate rejects same limb used twice`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(
                PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT)),
                PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT)),
            ),
            torsoFacings = emptyMap(),
        )
        val result = handler.validate(command, gameState, turnWithOneImpulse())
        assertThat(result).isInstanceOf(CommandRejection.RuleViolation::class.java)
        assertThat((result as CommandRejection.RuleViolation).rule)
            .isInstanceOf(RuleRejection.LimbAlreadyUsed::class.java)
    }
}
