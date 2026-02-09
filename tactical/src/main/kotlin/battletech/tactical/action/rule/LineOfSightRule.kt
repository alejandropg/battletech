package battletech.tactical.action.rule

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionRule
import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason
import battletech.tactical.model.Terrain

// Simplified: blocks LOS only if target hex is HEAVY_WOODS.
// Future expansion should consider intervening hexes and elevation.
public class LineOfSightRule : ActionRule {

    override fun evaluate(context: ActionContext): RuleResult {
        val target = context.target ?: return RuleResult.Satisfied
        val targetHex = context.gameState.map.hexes[target.position]
        return if (targetHex?.terrain == Terrain.HEAVY_WOODS) {
            RuleResult.Unsatisfied(
                UnavailabilityReason(
                    code = "NO_LINE_OF_SIGHT",
                    description = "No line of sight to target in heavy woods",
                )
            )
        } else {
            RuleResult.Satisfied
        }
    }
}
