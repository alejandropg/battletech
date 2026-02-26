package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason
import battletech.tactical.action.attack.AttackContext
import battletech.tactical.action.attack.AttackRule

public class AdjacentRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult {
        val distance = context.actor.position.distanceTo(context.target.position)
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
