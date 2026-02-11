package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.aWeapon
import battletech.tactical.action.attack.aWeaponAttackContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HasAmmoRuleTest {

    private val rule = HasAmmoRule()

    @Test
    fun `satisfied when weapon has ammo remaining`() {
        val result = rule.evaluate(aWeaponAttackContext(weapon = aWeapon(ammo = 10)))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `satisfied for energy weapons with no ammo tracking`() {
        val result = rule.evaluate(aWeaponAttackContext(weapon = aWeapon(ammo = null)))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `unsatisfied when ammo is zero`() {
        val result = rule.evaluate(aWeaponAttackContext(weapon = aWeapon(name = "SRM-4", ammo = 0)))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertEquals("NO_AMMO", unsatisfied.reason.code)
        assertThat(unsatisfied.reason.description).contains("SRM-4")
    }
}
