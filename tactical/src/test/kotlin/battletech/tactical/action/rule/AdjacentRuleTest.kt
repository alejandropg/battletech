package battletech.tactical.action.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.aUnit
import battletech.tactical.action.anActionContext
import battletech.tactical.model.HexCoordinates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AdjacentRuleTest {

    private val rule = AdjacentRule()

    @Test
    fun `satisfied when target is at distance one`() {
        val actor = aUnit(position = HexCoordinates(3, 3))
        val target = aUnit(id = "target", position = HexCoordinates(4, 3))

        val result = rule.evaluate(anActionContext(actor = actor, target = target))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `unsatisfied when target is at distance greater than one`() {
        val actor = aUnit(position = HexCoordinates(0, 0))
        val target = aUnit(id = "target", position = HexCoordinates(3, 0))

        val result = rule.evaluate(anActionContext(actor = actor, target = target))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertEquals("NOT_ADJACENT", unsatisfied.reason.code)
    }

    @Test
    fun `unsatisfied when target is at distance zero`() {
        val actor = aUnit(position = HexCoordinates(2, 2))
        val target = aUnit(id = "target", position = HexCoordinates(2, 2))

        val result = rule.evaluate(anActionContext(actor = actor, target = target))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
    }
}
