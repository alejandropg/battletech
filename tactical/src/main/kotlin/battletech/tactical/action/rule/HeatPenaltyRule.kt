package battletech.tactical.action.rule

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionRule
import battletech.tactical.action.RuleResult
import battletech.tactical.action.Warning
import kotlin.math.ceil

public class HeatPenaltyRule : ActionRule {

    override fun evaluate(context: ActionContext): RuleResult {
        val excessHeat = context.actor.currentHeat - context.actor.heatSinkCapacity
        return if (excessHeat <= 0) {
            RuleResult.Satisfied
        } else {
            val modifier = ceil(excessHeat / 3.0).toInt()
            RuleResult.Penalized(
                Warning(
                    code = "HEAT_PENALTY",
                    description = "Heat ${context.actor.currentHeat} exceeds capacity ${context.actor.heatSinkCapacity}, +$modifier to-hit modifier",
                    modifier = modifier,
                )
            )
        }
    }
}
