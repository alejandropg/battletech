package battletech.tactical.query

import battletech.tactical.attack.physical.PhysicalAttackKind
import battletech.tactical.attack.physical.Side
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.session.RuleRejection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import battletech.tactical.query.projectFor

internal class PhysicalAttackOptionsTest {

    private val attacker = aUnit(id = "attacker", tonnage = 50, position = HexCoordinates(0, 0))
    private fun enemyAt(col: Int) = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(col, 0))

    private fun viewWith(vararg units: battletech.tactical.unit.CombatUnit) =
        DefaultPlayerView(PlayerId.PLAYER_1, aGameState(units = units.toList()).projectFor(PlayerId.PLAYER_1))

    @Test
    fun `an adjacent enemy yields punch per arm and a kick`() {
        val enemy = enemyAt(1)
        val options = viewWith(attacker, enemy).physicalAttackOptions(attacker.id)

        assertThat(options.map { it.kind }).contains(
            PhysicalAttackKind.Punch(Side.LEFT),
            PhysicalAttackKind.Punch(Side.RIGHT),
        )
        assertThat(options.any { it.kind is PhysicalAttackKind.Kick }).isTrue()

        val punch = options.first { it.kind == PhysicalAttackKind.Punch(Side.LEFT) }
        assertThat(punch.available).isTrue()
        assertThat(punch.expectedDamage).isEqualTo(5) // ceil(50/10)
        assertThat(options.first { it.kind is PhysicalAttackKind.Kick }.expectedDamage).isEqualTo(10) // ceil(50/5)
    }

    @Test
    fun `a non-adjacent enemy yields no options`() {
        val enemy = enemyAt(3)
        assertThat(viewWith(attacker, enemy).physicalAttackOptions(attacker.id)).isEmpty()
    }

    @Test
    fun `a destroyed adjacent enemy yields no physical attack options`() {
        val destroyedEnemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 0), isDestroyed = true)
        assertThat(viewWith(attacker, destroyedEnemy).physicalAttackOptions(attacker.id)).isEmpty()
    }

    @Test
    fun `a destroyed arm makes that punch unavailable`() {
        val maimed = aUnit(
            id = "attacker",
            tonnage = 50,
            position = HexCoordinates(0, 0),
            internalStructure = anInternalStructureLayout(leftArm = 0),
        )
        val enemy = enemyAt(1)
        val options = viewWith(maimed, enemy).physicalAttackOptions(maimed.id)

        val leftPunch = options.first { it.kind == PhysicalAttackKind.Punch(Side.LEFT) }
        assertThat(leftPunch.available).isFalse()
        assertThat(leftPunch.unavailableReasons).anyMatch { it is RuleRejection.LimbDestroyed }
        assertThat(options.first { it.kind == PhysicalAttackKind.Punch(Side.RIGHT) }.available).isTrue()
    }
}
