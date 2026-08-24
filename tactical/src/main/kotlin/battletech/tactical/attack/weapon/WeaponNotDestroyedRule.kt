package battletech.tactical.attack.weapon

import battletech.tactical.rules.RuleResult
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.WeaponAttackContext
import battletech.tactical.rules.RuleRejection

public class WeaponNotDestroyedRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val weapon = context.weapon
        return if (!weapon.destroyed) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(RuleRejection.WeaponDestroyed(weaponName = weapon.name))
        }
    }
}
