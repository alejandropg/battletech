package battletech.tactical.attack.weapon

import battletech.tactical.query.RuleResult
import battletech.tactical.query.Warning
import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
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
