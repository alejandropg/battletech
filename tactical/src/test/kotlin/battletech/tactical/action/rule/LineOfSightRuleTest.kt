package battletech.tactical.action.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.aGameState
import battletech.tactical.action.aUnit
import battletech.tactical.action.anActionContext
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LineOfSightRuleTest {

    private val rule = LineOfSightRule()

    @Test
    fun `satisfied when target is in clear terrain`() {
        val targetPos = HexCoordinates(3, 0)
        val target = aUnit(id = "target", position = targetPos)
        val hexes = mapOf(targetPos to Hex(targetPos, Terrain.CLEAR))
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(anActionContext(target = target, gameState = gameState))

        assertThat(result).isEqualTo(RuleResult.Satisfied)
    }

    @Test
    fun `unsatisfied when target is in heavy woods`() {
        val targetPos = HexCoordinates(3, 0)
        val target = aUnit(id = "target", position = targetPos)
        val hexes = mapOf(targetPos to Hex(targetPos, Terrain.HEAVY_WOODS))
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(anActionContext(target = target, gameState = gameState))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertThat(unsatisfied.reason.code).isEqualTo("NO_LINE_OF_SIGHT")
    }

    @Test
    fun `satisfied when target is in light woods`() {
        val targetPos = HexCoordinates(3, 0)
        val target = aUnit(id = "target", position = targetPos)
        val hexes = mapOf(targetPos to Hex(targetPos, Terrain.LIGHT_WOODS))
        val gameState = aGameState(hexes = hexes)

        val result = rule.evaluate(anActionContext(target = target, gameState = gameState))

        assertThat(result).isEqualTo(RuleResult.Satisfied)
    }
}
