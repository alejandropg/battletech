package io.archinaut.battletech.tactical.attack.weapon

import io.archinaut.battletech.tactical.rules.RuleResult
import io.archinaut.battletech.tactical.rules.Warning
import io.archinaut.battletech.tactical.attack.AttackContext
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.heat.HeatScale

public class HeatPenaltyRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult {
        val actor = context.actor
        val modifier = HeatScale.toHitPenalty(actor.currentHeat)
        return if (modifier == 0) {
            RuleResult.Satisfied
        } else {
            RuleResult.Penalized(
                Warning(
                    code = "HEAT_PENALTY",
                    description = "Heat ${actor.currentHeat}, +$modifier to-hit modifier",
                    modifier = modifier,
                )
            )
        }
    }
}
