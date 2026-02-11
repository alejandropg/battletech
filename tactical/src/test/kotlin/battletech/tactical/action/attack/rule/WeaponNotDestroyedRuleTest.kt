package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.aWeapon
import battletech.tactical.action.attack.aWeaponAttackContext
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
        assertEquals("WEAPON_DESTROYED", unsatisfied.reason.code)
        assertThat(unsatisfied.reason.description).contains("AC/20")
    }
}
