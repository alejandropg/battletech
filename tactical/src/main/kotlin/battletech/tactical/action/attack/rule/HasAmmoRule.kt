package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason
import battletech.tactical.action.attack.AttackRule
import battletech.tactical.action.attack.WeaponAttackContext

public class HasAmmoRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val weapon = context.weapon
        return if (weapon.ammo == null || weapon.ammo > 0) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(
                UnavailabilityReason(
                    code = "NO_AMMO",
                    description = "${weapon.name} has no ammo remaining",
                )
            )
        }
    }
}
