package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.physical.PhysicalAttackKind.Kick
import battletech.tactical.attack.physical.PhysicalAttackKind.Punch
import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PhysicalKickKnockdownTest {

    private val attackerPos = HexCoordinates(0, 0)
    private val targetPos = HexCoordinates(1, 0)
    private val bearing = FiringArc.bearingDirection(targetPos, attackerPos)

    private val attacker = aUnit(id = "attacker", tonnage = 50, pilotingSkill = 5, position = attackerPos)
    private val target = aUnit(id = "target", pilotingSkill = 5, position = targetPos, facing = bearing)
    private val state = aGameState(units = listOf(attacker, target))

    private fun kick() = PhysicalAttackDeclaration(attacker.id, target.id, Kick(Side.RIGHT))

    @Test
    fun `a landed kick that fails the target PSR knocks the target down`() {
        // to-hit TN = 5 - 2 = 3; roll 2+2 = 4 (hit); location 1 -> Right Leg.
        // target PSR TN 5; roll 1+1 = 2 (fail). fall: location 3+4 = 7 -> CT; facing 1 -> no change.
        // pilot hit 1 consciousness 2d6 (3,3 → 6 ≥ target 3 → conscious).
        val roller = DiceRoller.deterministic(2, 2, 1, 1, 1, 3, 4, 1, 3, 3)
        val (newState, results) = resolvePhysicalAttacks(listOf(kick()), state, roller)

        val result = results.single()
        assertThat(result).isInstanceOf(PhysicalAttackResult.Hit::class.java)
        val knockdown = result.knockdown
        assertThat(knockdown).isInstanceOf(Knockdown.Fell.Detailed::class.java)
        knockdown as Knockdown.Fell.Detailed
        assertThat(knockdown.psr.passed).isFalse()
        assertThat(knockdown.unitId).isEqualTo(target.id)

        val fallenTarget = newState.unitById(target.id)!!
        assertThat(fallenTarget.isProne).isTrue()
        // kick damage (ceil(50/5)=10) to Right Leg, plus fall damage (ceil(50/10)=5) to Center Torso.
        assertThat(fallenTarget.armor.rightLeg).isEqualTo(target.armor.rightLeg - 10)
        assertThat(fallenTarget.armor.centerTorso).isEqualTo(target.armor.centerTorso - 5)
        assertThat((result as PhysicalAttackResult.Hit).hitLocation).isEqualTo(HitLocation.RIGHT_LEG)
    }

    @Test
    fun `a missed kick that fails the attacker PSR knocks the attacker down`() {
        // to-hit TN 3; roll 1+1 = 2 (miss). attacker PSR TN 5; roll 1+1 = 2 (fail).
        // fall: location 3+4 = 7 -> CT; facing 1.
        // pilot hit 1 consciousness 2d6 (3,3 → 6 ≥ target 3 → conscious).
        val roller = DiceRoller.deterministic(1, 1, 1, 1, 3, 4, 1, 3, 3)
        val (newState, results) = resolvePhysicalAttacks(listOf(kick()), state, roller)

        val result = results.single()
        assertThat(result).isInstanceOf(PhysicalAttackResult.Miss::class.java)
        val knockdown = result.knockdown
        assertThat(knockdown).isInstanceOf(Knockdown.Fell.Detailed::class.java)
        assertThat((knockdown as Knockdown.Fell.Detailed).unitId).isEqualTo(attacker.id)

        val fallenAttacker = newState.unitById(attacker.id)!!
        assertThat(fallenAttacker.isProne).isTrue()
        assertThat(fallenAttacker.armor.centerTorso).isEqualTo(attacker.armor.centerTorso - 5)
    }

    @Test
    fun `a landed kick whose target passes the PSR causes no fall`() {
        // hit (2+2); location 1; target PSR 6+6 = 12 (pass).
        val roller = DiceRoller.deterministic(2, 2, 1, 6, 6)
        val (newState, results) = resolvePhysicalAttacks(listOf(kick()), state, roller)

        val result = results.single()
        val knockdown = result.knockdown
        assertThat(knockdown).isInstanceOf(Knockdown.Resisted.Detailed::class.java)
        assertThat((knockdown as Knockdown.Resisted.Detailed).psr.passed).isTrue()
        assertThat(newState.unitById(target.id)!!.isProne).isFalse()
    }

    @Test
    fun `a punch never triggers a piloting skill roll`() {
        val punch = PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT))
        val roller = DiceRoller.deterministic(3, 3, 1)
        val (_, results) = resolvePhysicalAttacks(listOf(punch), state, roller)

        assertThat(results.single().knockdown).isEqualTo(Knockdown.None)
    }
}
