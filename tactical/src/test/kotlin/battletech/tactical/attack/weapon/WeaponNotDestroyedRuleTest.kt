package battletech.tactical.attack.weapon

import battletech.tactical.query.RuleResult
import battletech.tactical.query.aWeapon
import battletech.tactical.attack.aWeaponAttackContext
import battletech.tactical.session.RuleRejection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class WeaponNotDestroyedRuleTest {

    private val rule = WeaponNotDestroyedRule()

    @Test
    fun `satisfied when weapon is functional`() {
        val result = rule.evaluate(aWeaponAttackContext(weapon = aWeapon(destroyed = false)))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `unsatisfied when weapon is destroyed`() {
        val result = rule.evaluate(aWeaponAttackContext(weapon = aWeapon(name = "AC/20", destroyed = true)))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertThat(unsatisfied.reason).isInstanceOf(RuleRejection.WeaponDestroyed::class.java)
        val destroyed = unsatisfied.reason as RuleRejection.WeaponDestroyed
        assertEquals("AC/20", destroyed.weaponName)
    }
}
