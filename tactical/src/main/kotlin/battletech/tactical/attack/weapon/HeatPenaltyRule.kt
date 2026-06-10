package battletech.tactical.attack.weapon

import battletech.tactical.query.RuleResult
import battletech.tactical.query.Warning
import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
import battletech.tactical.heat.HeatScale

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
