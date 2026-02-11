package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.Warning
import battletech.tactical.action.attack.AttackContext
import battletech.tactical.action.attack.AttackRule
import kotlin.math.ceil

public class HeatPenaltyRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult {
        val actor = context.actor
        val excessHeat = actor.currentHeat - actor.heatSinkCapacity
        return if (excessHeat <= 0) {
            RuleResult.Satisfied
        } else {
            val modifier = ceil(excessHeat / 3.0).toInt()
            RuleResult.Penalized(
                Warning(
                    code = "HEAT_PENALTY",
                    description = "Heat ${actor.currentHeat} exceeds capacity ${actor.heatSinkCapacity}, +$modifier to-hit modifier",
                    modifier = modifier,
                )
            )
        }
    }
}
