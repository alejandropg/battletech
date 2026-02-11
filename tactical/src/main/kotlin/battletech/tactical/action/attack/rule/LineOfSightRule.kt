package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason
import battletech.tactical.action.attack.AttackContext
import battletech.tactical.action.attack.AttackRule
import battletech.tactical.model.Terrain

// Simplified: blocks LOS only if target hex is HEAVY_WOODS.
// Future expansion should consider intervening hexes and elevation.
public class LineOfSightRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult {
        val targetHex = context.gameState.map.hexes[context.target.position]
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
