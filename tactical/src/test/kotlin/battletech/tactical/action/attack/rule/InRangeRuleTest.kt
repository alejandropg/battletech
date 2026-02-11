package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.aUnit
import battletech.tactical.action.aWeapon
import battletech.tactical.action.attack.aWeaponAttackContext
import battletech.tactical.model.HexCoordinates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class InRangeRuleTest {

    private val rule = InRangeRule()

    @Test
    fun `satisfied when target is within range`() {
        val actor = aUnit(position = HexCoordinates(0, 0))
        val target = aUnit(id = "target", position = HexCoordinates(2, 0))
        val weapon = aWeapon(longRange = 9)

        val result = rule.evaluate(aWeaponAttackContext(actor = actor, target = target, weapon = weapon))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `satisfied at exactly maximum range`() {
        val actor = aUnit(position = HexCoordinates(0, 0))
        val target = aUnit(id = "target", position = HexCoordinates(9, 0))
        val weapon = aWeapon(longRange = 9)

        val result = rule.evaluate(aWeaponAttackContext(actor = actor, target = target, weapon = weapon))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `unsatisfied when target is beyond range`() {
        val actor = aUnit(position = HexCoordinates(0, 0))
        val target = aUnit(id = "target", position = HexCoordinates(10, 0))
        val weapon = aWeapon(name = "Medium Laser", longRange = 9)

        val result = rule.evaluate(aWeaponAttackContext(actor = actor, target = target, weapon = weapon))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertEquals("OUT_OF_RANGE", unsatisfied.reason.code)
        assertThat(unsatisfied.reason.description).contains("10")
        assertThat(unsatisfied.reason.description).contains("9")
    }
}
