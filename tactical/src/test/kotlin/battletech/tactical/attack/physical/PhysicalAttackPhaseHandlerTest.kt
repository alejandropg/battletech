package battletech.tactical.attack.physical

import battletech.tactical.attack.physical.PhysicalAttackKind.Kick
import battletech.tactical.attack.physical.PhysicalAttackKind.Punch
import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommitPhysicalAttackImpulse
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.PhysicalAttacksResolved
import battletech.tactical.session.RuleRejection
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitFell
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
        attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
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
        assertThat(outcome.turn.physicalAttackDeclarations).isEmpty()
    }

    @Test
    fun `a kick knockdown emits a UnitFell event`() {
        val command = CommitPhysicalAttackImpulse(
            playerId = PlayerId.PLAYER_1,
            declarations = listOf(PhysicalAttackDeclaration(attacker.id, target.id, Kick(Side.RIGHT))),
            torsoFacings = emptyMap(),
        )
        // hit (2+2, TN 3); location 1; target PSR 1+1 fail; fall 3+4 -> CT; facing 1.
        val roller = DiceRoller.deterministic(2, 2, 1, 1, 1, 3, 4, 1)

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
}
