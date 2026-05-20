package battletech.tactical.attack.physical

import battletech.tactical.action.RuleResult
import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
import battletech.tactical.session.RuleRejection

public class AdjacentRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult {
        val distance = context.actor.position.distanceTo(context.target.position)
        return if (distance == 1) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(RuleRejection.NotAdjacent(distance = distance))
        }
    }
}
