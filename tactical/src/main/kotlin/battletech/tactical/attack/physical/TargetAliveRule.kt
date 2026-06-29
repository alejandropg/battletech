package battletech.tactical.attack.physical

import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection

public class TargetAliveRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult =
        if (!context.target.isDestroyed) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(RuleRejection.TargetDestroyed)
        }
}
