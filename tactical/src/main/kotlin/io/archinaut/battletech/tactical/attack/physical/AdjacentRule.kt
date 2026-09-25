package io.archinaut.battletech.tactical.attack.physical

import io.archinaut.battletech.tactical.rules.RuleResult
import io.archinaut.battletech.tactical.attack.AttackContext
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.rules.RuleRejection

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
