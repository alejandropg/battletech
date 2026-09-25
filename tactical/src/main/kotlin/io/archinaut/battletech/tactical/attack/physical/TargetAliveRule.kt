package io.archinaut.battletech.tactical.attack.physical

import io.archinaut.battletech.tactical.attack.AttackContext
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.rules.RuleResult
import io.archinaut.battletech.tactical.rules.RuleRejection

public class TargetAliveRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult =
        if (!context.target.isDestroyed) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(RuleRejection.TargetDestroyed)
        }
}
