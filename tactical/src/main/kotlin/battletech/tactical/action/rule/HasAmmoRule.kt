package battletech.tactical.action.rule

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionRule
import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason

public class HasAmmoRule : ActionRule {

    override fun evaluate(context: ActionContext): RuleResult {
        val weapon = context.weapon ?: return RuleResult.Satisfied
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
