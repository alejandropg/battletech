package battletech.tactical.attack.weapon

import battletech.tactical.query.RuleResult
import battletech.tactical.query.aWeapon
import battletech.tactical.attack.aWeaponAttackContext
import battletech.tactical.session.RuleRejection
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
        assertThat(unsatisfied.reason).isInstanceOf(RuleRejection.NoAmmo::class.java)
        val noAmmo = unsatisfied.reason as RuleRejection.NoAmmo
        assertEquals("SRM-4", noAmmo.weaponName)
    }
}
