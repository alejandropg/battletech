package battletech.tactical.action.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.anActionContext
import battletech.tactical.action.aWeapon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HasAmmoRuleTest {

    private val rule = HasAmmoRule()

    @Test
    fun `satisfied when weapon has ammo remaining`() {
        val context = anActionContext(weapon = aWeapon(ammo = 10))

        val result = rule.evaluate(context)

        assertThat(result).isEqualTo(RuleResult.Satisfied)
    }

    @Test
    fun `satisfied for energy weapons with no ammo tracking`() {
        val context = anActionContext(weapon = aWeapon(ammo = null))

        val result = rule.evaluate(context)

        assertThat(result).isEqualTo(RuleResult.Satisfied)
    }

    @Test
    fun `unsatisfied when ammo is zero`() {
        val context = anActionContext(weapon = aWeapon(name = "SRM-4", ammo = 0))

        val result = rule.evaluate(context)

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertThat(unsatisfied.reason.code).isEqualTo("NO_AMMO")
        assertThat(unsatisfied.reason.description).contains("SRM-4")
    }
}
