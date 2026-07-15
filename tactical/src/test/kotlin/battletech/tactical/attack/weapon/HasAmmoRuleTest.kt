package battletech.tactical.attack.weapon

import battletech.tactical.model.MechLocation
import battletech.tactical.query.RuleResult
import battletech.tactical.query.aUnit
import battletech.tactical.query.aWeapon
import battletech.tactical.attack.aWeaponAttackContext
import battletech.tactical.session.RuleRejection
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.WeaponKind
import battletech.tactical.unit.mechLayout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HasAmmoRuleTest {

    private val rule = HasAmmoRule()

    @Test
    fun `satisfied when weapon has ammo remaining`() {
        val layout = mechLayout { ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1) }.layout
        val actor = aUnit(criticalLayout = layout)
        val result = rule.evaluate(aWeaponAttackContext(actor = actor, weapon = aWeapon(kind = WeaponKind.Ballistic(AmmoType.AC20))))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `satisfied for energy weapons with no ammo tracking`() {
        val result = rule.evaluate(aWeaponAttackContext(weapon = aWeapon()))

        assertEquals(RuleResult.Satisfied, result)
    }

    @Test
    fun `unsatisfied when ammo is zero`() {
        // No matching ammo bin in the actor's layout for AC20 -> zero remaining rounds.
        val result = rule.evaluate(aWeaponAttackContext(weapon = aWeapon(name = "SRM-4", kind = WeaponKind.Ballistic(AmmoType.AC20))))

        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertThat(unsatisfied.reason).isInstanceOf(RuleRejection.NoAmmo::class.java)
        val noAmmo = unsatisfied.reason as RuleRejection.NoAmmo
        assertEquals("SRM-4", noAmmo.weaponName)
    }
}
