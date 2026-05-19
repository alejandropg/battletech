package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.attack.AttackRule
import battletech.tactical.action.attack.WeaponAttackContext
import battletech.tactical.command.RuleRejection

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
