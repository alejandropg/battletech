package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason
import battletech.tactical.action.attack.AttackRule
import battletech.tactical.action.attack.WeaponAttackContext

public class WeaponNotDestroyedRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val weapon = context.weapon
        return if (!weapon.destroyed) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(
                UnavailabilityReason(
                    code = "WEAPON_DESTROYED",
                    description = "${weapon.name} is destroyed",
                )
            )
        }
    }
}
