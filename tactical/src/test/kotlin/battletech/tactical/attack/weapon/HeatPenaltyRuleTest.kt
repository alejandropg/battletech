package battletech.tactical.attack.weapon

import battletech.tactical.query.RuleResult
import battletech.tactical.attack.aPhysicalAttackContext
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HeatPenaltyRuleTest {

    private val rule = HeatPenaltyRule()

    @Test
    fun `satisfied below the first heat threshold regardless of capacity`() {
        val actor = aUnit(currentHeat = 7, heatSinkCapacity = 0)

        val result = rule.evaluate(aPhysicalAttackContext(actor = actor))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `penalized once heat reaches the table threshold`() {
        val actor = aUnit(currentHeat = 8, heatSinkCapacity = 20)

        val result = rule.evaluate(aPhysicalAttackContext(actor = actor))

        assertThat(result).isInstanceOf(RuleResult.Penalized::class.java)
        val penalized = result as RuleResult.Penalized
        assertEquals("HEAT_PENALTY", penalized.warning.code)
        assertEquals(1, penalized.warning.modifier)
    }

    @Test
    fun `modifier follows the heat scale`() {
        val actor = aUnit(currentHeat = 18, heatSinkCapacity = 10)

        val result = rule.evaluate(aPhysicalAttackContext(actor = actor))

        val penalized = result as RuleResult.Penalized
        assertEquals(3, penalized.warning.modifier)
    }

    @Test
    fun `description includes the heat value`() {
        val actor = aUnit(currentHeat = 14, heatSinkCapacity = 10)

        val result = rule.evaluate(aPhysicalAttackContext(actor = actor))

        val penalized = result as RuleResult.Penalized
        assertThat(penalized.warning.description).contains("14")
    }
}
