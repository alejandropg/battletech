package battletech.tactical.action.rule

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionRule
import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason

public class InRangeRule : ActionRule {

    override fun evaluate(context: ActionContext): RuleResult {
        val weapon = context.weapon ?: return RuleResult.Satisfied
        val target = context.target ?: return RuleResult.Satisfied
        val distance = context.actor.position.distanceTo(target.position)
        return if (distance <= weapon.longRange) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(
                UnavailabilityReason(
                    code = "OUT_OF_RANGE",
                    description = "${weapon.name} target is at distance $distance, max range is ${weapon.longRange}",
                )
            )
        }
    }
}
