package battletech.tactical.action.rule

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionRule
import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason

public class AdjacentRule : ActionRule {

    override fun evaluate(context: ActionContext): RuleResult {
        val target = context.target ?: return RuleResult.Satisfied
        val distance = context.actor.position.distanceTo(target.position)
        return if (distance == 1) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(
                UnavailabilityReason(
                    code = "NOT_ADJACENT",
                    description = "Target is at distance $distance, must be adjacent (distance 1)",
                )
            )
        }
    }
}
