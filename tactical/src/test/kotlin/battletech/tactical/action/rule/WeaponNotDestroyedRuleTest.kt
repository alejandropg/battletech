package battletech.tactical.action.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.anActionContext
import battletech.tactical.action.aWeapon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class WeaponNotDestroyedRuleTest {

    private val rule = WeaponNotDestroyedRule()

    @Test
    fun `satisfied when weapon is functional`() {
        val context = anActionContext(weapon = aWeapon(destroyed = false))

        val result = rule.evaluate(context)

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `unsatisfied when weapon is destroyed`() {
        val context = anActionContext(weapon = aWeapon(name = "AC/20", destroyed = true))

        val result = rule.evaluate(context)

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertEquals("WEAPON_DESTROYED", unsatisfied.reason.code)
        assertThat(unsatisfied.reason.description).contains("AC/20")
    }
}
