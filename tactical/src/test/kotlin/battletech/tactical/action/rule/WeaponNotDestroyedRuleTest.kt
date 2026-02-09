package battletech.tactical.action.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.anActionContext
import battletech.tactical.action.aWeapon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class WeaponNotDestroyedRuleTest {

    private val rule = WeaponNotDestroyedRule()

    @Test
    fun `satisfied when weapon is functional`() {
        val context = anActionContext(weapon = aWeapon(destroyed = false))

        val result = rule.evaluate(context)

        assertThat(result).isEqualTo(RuleResult.Satisfied)
    }

    @Test
    fun `unsatisfied when weapon is destroyed`() {
        val context = anActionContext(weapon = aWeapon(name = "AC/20", destroyed = true))

        val result = rule.evaluate(context)

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertThat(unsatisfied.reason.code).isEqualTo("WEAPON_DESTROYED")
        assertThat(unsatisfied.reason.description).contains("AC/20")
    }
}
