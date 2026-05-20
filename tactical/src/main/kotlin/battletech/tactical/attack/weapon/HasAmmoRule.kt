package battletech.tactical.attack.weapon

import battletech.tactical.action.RuleResult
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.WeaponAttackContext
import battletech.tactical.session.RuleRejection

public class HasAmmoRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val weapon = context.weapon
        return if (weapon.ammo == null || weapon.ammo > 0) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(RuleRejection.NoAmmo(weaponName = weapon.name))
        }
    }
}
