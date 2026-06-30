package battletech.tactical.attack.weapon

import battletech.tactical.attack.aWeaponAttackContext
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import battletech.tactical.query.RuleResult
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.session.RuleRejection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LineOfSightRuleTest {

    private val rule = LineOfSightRule()

    // Attacker at (0,0), target at (0,2): straight column, intervening hex (0,1).
    private val attackerPos = HexCoordinates(0, 0)
    private val targetPos = HexCoordinates(0, 2)
    private val interveningPos = HexCoordinates(0, 1)

    @Test
    fun `satisfied when target is in clear terrain with no blocking intervening hexes`() {
        val target = aUnit(id = "target", position = targetPos)
        val hexes = mapOf(targetPos to Hex(targetPos, Terrain.CLEAR))
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(aWeaponAttackContext(target = target, gameState = gameState))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `satisfied when target is in heavy woods — target hex alone does not block`() {
        // The target's own hex never counts toward the blocking threshold;
        // it only adds a +2 terrain to-hit modifier.
        val target = aUnit(id = "target", position = targetPos)
        val hexes = mapOf(targetPos to Hex(targetPos, Terrain.HEAVY_WOODS))
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(aWeaponAttackContext(target = target, gameState = gameState))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `satisfied when target is in light woods`() {
        val target = aUnit(id = "target", position = targetPos)
        val hexes = mapOf(targetPos to Hex(targetPos, Terrain.LIGHT_WOODS))
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(aWeaponAttackContext(target = target, gameState = gameState))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `satisfied when single intervening heavy woods hex — 2 levels below blocking threshold`() {
        val target = aUnit(id = "target", position = targetPos)
        val hexes = mapOf(interveningPos to Hex(interveningPos, Terrain.HEAVY_WOODS))
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(aWeaponAttackContext(target = target, gameState = gameState))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `unsatisfied when two intervening heavy woods hexes block LOS — 4 levels reach threshold`() {
        // Attacker (0,0) → target (0,3): intervening hexes are (0,1) and (0,2).
        val farTargetPos = HexCoordinates(0, 3)
        val target = aUnit(id = "target", position = farTargetPos)
        val hex1 = HexCoordinates(0, 1)
        val hex2 = HexCoordinates(0, 2)
        val hexes = mapOf(
            hex1 to Hex(hex1, Terrain.HEAVY_WOODS),
            hex2 to Hex(hex2, Terrain.HEAVY_WOODS),
        )
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(aWeaponAttackContext(target = target, gameState = gameState))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        assertThat((result as RuleResult.Unsatisfied).reason).isInstanceOf(RuleRejection.NoLineOfSight::class.java)
    }

    @Test
    fun `unsatisfied when heavy plus light intervening woods reach blocking threshold`() {
        // (0,1) HEAVY = 2 levels, (0,2) LIGHT = 1 level → total 3 ≥ 3 → blocked.
        val farTargetPos = HexCoordinates(0, 3)
        val target = aUnit(id = "target", position = farTargetPos)
        val hex1 = HexCoordinates(0, 1)
        val hex2 = HexCoordinates(0, 2)
        val hexes = mapOf(
            hex1 to Hex(hex1, Terrain.HEAVY_WOODS),
            hex2 to Hex(hex2, Terrain.LIGHT_WOODS),
        )
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(aWeaponAttackContext(target = target, gameState = gameState))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
    }

    @Test
    fun `unsatisfied when intervening hex elevation exceeds both endpoints`() {
        val target = aUnit(id = "target", position = targetPos)
        // Attacker and target at elevation 0 (default). Intervening hex at elevation 3.
        val hexes = mapOf(interveningPos to Hex(interveningPos, Terrain.CLEAR, elevation = 3))
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(aWeaponAttackContext(target = target, gameState = gameState))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val noLos = (result as RuleResult.Unsatisfied).reason as RuleRejection.NoLineOfSight
        assertEquals(interveningPos, noLos.blockerAt)
    }
}
