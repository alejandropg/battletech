package battletech.tactical.action.rule

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionRule
import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason

public class WeaponNotDestroyedRule : ActionRule {

    override fun evaluate(context: ActionContext): RuleResult {
        val weapon = context.weapon ?: return RuleResult.Satisfied
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
