package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.attack.aPhysicalAttackContext
import battletech.tactical.action.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HeatPenaltyRuleTest {

    private val rule = HeatPenaltyRule()

    @Test
    fun `satisfied when heat is at capacity`() {
        val actor = aUnit(currentHeat = 10, heatSinkCapacity = 10)

        val result = rule.evaluate(aPhysicalAttackContext(actor = actor))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `satisfied when heat is below capacity`() {
        val actor = aUnit(currentHeat = 5, heatSinkCapacity = 10)

        val result = rule.evaluate(aPhysicalAttackContext(actor = actor))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `penalized when heat exceeds capacity`() {
        val actor = aUnit(currentHeat = 13, heatSinkCapacity = 10)

        val result = rule.evaluate(aPhysicalAttackContext(actor = actor))

        assertThat(result).isInstanceOf(RuleResult.Penalized::class.java)
        val penalized = result as RuleResult.Penalized
        assertEquals("HEAT_PENALTY", penalized.warning.code)
        assertEquals(1, penalized.warning.modifier)
    }

    @Test
    fun `modifier rounds up excess heat divided by three`() {
        val actor = aUnit(currentHeat = 16, heatSinkCapacity = 10)

        val result = rule.evaluate(aPhysicalAttackContext(actor = actor))

        val penalized = result as RuleResult.Penalized
        assertEquals(2, penalized.warning.modifier)
    }

    @Test
    fun `description includes heat values`() {
        val actor = aUnit(currentHeat = 14, heatSinkCapacity = 10)

        val result = rule.evaluate(aPhysicalAttackContext(actor = actor))

        val penalized = result as RuleResult.Penalized
        assertThat(penalized.warning.description).contains("14")
        assertThat(penalized.warning.description).contains("10")
    }
}
