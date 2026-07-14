package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.physical.PhysicalAttackKind.Punch
import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PhysicalAttackResolutionTest {

    private val attackerPos = HexCoordinates(0, 0)
    private val targetPos = HexCoordinates(1, 0)
    private val bearing = FiringArc.bearingDirection(targetPos, attackerPos)

    @Test
    fun `a landing punch applies tonnage-based damage to the rolled location`() {
        // Target faces the attacker -> front attack. Punch front column, die 1 -> Left Arm.
        val attacker = aUnit(id = "attacker", tonnage = 50, pilotingSkill = 5, position = attackerPos)
        val target = aUnit(id = "target", position = targetPos, facing = bearing)
        val state = aGameState(units = listOf(attacker, target))
        val declaration = PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT))

        // to-hit needs >= 5 on 2d6; roll 3+3 = 6 (hit); then 1d6 = 1 -> Left Arm.
        val roller = DiceRoller.deterministic(3, 3, 1)
        val (newState, results) = resolvePhysicalAttacks(listOf(declaration), state, roller)

        val result = results.single()
        assertThat(result).isInstanceOf(PhysicalAttackResult.Hit::class.java)
        result as PhysicalAttackResult.Hit
        assertThat(result.hitLocation).isEqualTo(HitLocation.LEFT_ARM)
        assertThat(result.damageApplied).isEqualTo(5)

        val damagedTarget = newState.unitById(target.id)!!
        assertThat(damagedTarget.armor.leftArm).isEqualTo(target.armor.leftArm - 5)
    }

    @Test
    fun `resolution to-hit reflects attacker movement`() {
        val attacker = aUnit(id = "attacker", tonnage = 50, pilotingSkill = 5, position = attackerPos)
            .copy(movementThisTurn = MovementThisTurn.Moved(MovementMode.WALK, 2))
        val target = aUnit(id = "target", position = targetPos, facing = bearing)
        val state = aGameState(units = listOf(attacker, target))
        val declaration = PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT))

        val roller = DiceRoller.deterministic(3, 3, 1)
        val (_, results) = resolvePhysicalAttacks(listOf(declaration), state, roller)

        // piloting 5 + walked 1 = 6.
        assertThat(results.single().targetNumber).isEqualTo(6)
    }

    @Test
    fun `a missed punch applies no damage`() {
        val attacker = aUnit(id = "attacker", tonnage = 50, pilotingSkill = 5, position = attackerPos)
        val target = aUnit(id = "target", position = targetPos, facing = bearing)
        val state = aGameState(units = listOf(attacker, target))
        val declaration = PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT))

        // to-hit needs >= 5; roll 1+1 = 2 -> miss.
        val roller = DiceRoller.deterministic(1, 1)
        val (newState, results) = resolvePhysicalAttacks(listOf(declaration), state, roller)

        val result = results.single()
        assertThat(result).isInstanceOf(PhysicalAttackResult.Miss::class.java)
        assertThat(newState.unitById(target.id)!!.armor).isEqualTo(target.armor)
    }

    @Test
    fun `a rear-arc torso hit depletes rear armor`() {
        // Make the target face away from the attacker -> rear attack.
        val rearFacing = HexDirection.entries[(bearing.ordinal + 3) % 6]
        val attacker = aUnit(id = "attacker", tonnage = 50, pilotingSkill = 5, position = attackerPos)
        val target = aUnit(id = "target", position = targetPos, facing = rearFacing)
        val state = aGameState(units = listOf(attacker, target))
        val declaration = PhysicalAttackDeclaration(attacker.id, target.id, Punch(Side.LEFT))

        // hit (3+3=6 >= 5); location die 3 -> Center Torso on the front/rear column.
        val roller = DiceRoller.deterministic(3, 3, 3)
        val (newState, results) = resolvePhysicalAttacks(listOf(declaration), state, roller)

        assertThat((results.single() as PhysicalAttackResult.Hit).hitLocation).isEqualTo(HitLocation.CENTER_TORSO)
        val damaged = newState.unitById(target.id)!!
        assertThat(damaged.armor.centerTorsoRear).isEqualTo(target.armor.centerTorsoRear - 5)
        assertThat(damaged.armor.centerTorso).isEqualTo(target.armor.centerTorso)
    }
}
