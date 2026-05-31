package battletech.tactical.attack.physical

import battletech.tactical.attack.physical.PhysicalAttackKind.Kick
import battletech.tactical.attack.physical.PhysicalAttackKind.Punch
import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.session.RuleRejection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PhysicalAttackLimitsTest {

    private val attacker = aUnit(id = "attacker", position = HexCoordinates(0, 0))
    private val target = aUnit(id = "target", position = HexCoordinates(1, 0))
    private val gameState = aGameState(units = listOf(attacker, target))

    private fun punch(arm: Side) =
        PhysicalAttackDeclaration(attacker.id, target.id, Punch(arm))

    private fun kick(leg: Side) =
        PhysicalAttackDeclaration(attacker.id, target.id, Kick(leg))

    @Test
    fun `punching twice with the same arm is illegal`() {
        val violation = physicalImpulseViolation(
            listOf(punch(Side.LEFT), punch(Side.LEFT)),
            gameState,
        )

        assertThat(violation).isInstanceOf(RuleRejection.LimbAlreadyUsed::class.java)
    }

    @Test
    fun `punching and kicking in the same turn is illegal`() {
        val violation = physicalImpulseViolation(
            listOf(punch(Side.LEFT), kick(Side.RIGHT)),
            gameState,
        )

        assertThat(violation).isInstanceOf(RuleRejection.PunchAndKickSameTurn::class.java)
    }

    @Test
    fun `punching with both arms at the same target is allowed`() {
        val violation = physicalImpulseViolation(
            listOf(punch(Side.LEFT), punch(Side.RIGHT)),
            gameState,
        )

        assertThat(violation).isNull()
    }

    @Test
    fun `kicking with both legs is illegal`() {
        val violation = physicalImpulseViolation(
            listOf(kick(Side.LEFT), kick(Side.RIGHT)),
            gameState,
        )

        assertThat(violation).isInstanceOf(RuleRejection.LimbAlreadyUsed::class.java)
    }

    @Test
    fun `punching with a destroyed arm is illegal`() {
        val maimed = aUnit(
            id = "attacker",
            position = HexCoordinates(0, 0),
            internalStructure = anInternalStructureLayout(leftArm = 0),
        )
        val state = aGameState(units = listOf(maimed, target))

        val violation = physicalImpulseViolation(listOf(punch(Side.LEFT)), state)

        assertThat(violation).isInstanceOf(RuleRejection.LimbDestroyed::class.java)
    }

    @Test
    fun `kicking with a destroyed leg is illegal`() {
        val maimed = aUnit(
            id = "attacker",
            position = HexCoordinates(0, 0),
            internalStructure = anInternalStructureLayout(rightLeg = 0),
        )
        val state = aGameState(units = listOf(maimed, target))

        val violation = physicalImpulseViolation(listOf(kick(Side.RIGHT)), state)

        assertThat(violation).isInstanceOf(RuleRejection.LimbDestroyed::class.java)
    }
}
