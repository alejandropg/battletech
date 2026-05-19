package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.attack.AttackContext
import battletech.tactical.action.attack.AttackRule
import battletech.tactical.command.RuleRejection
import battletech.tactical.model.Terrain

// Simplified: blocks LOS only if target hex is HEAVY_WOODS.
// Future expansion should consider intervening hexes and elevation.
public class LineOfSightRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult {
        val targetHex = context.gameState.map.hexes[context.target.position]
        return if (targetHex?.terrain == Terrain.HEAVY_WOODS) {
            RuleResult.Unsatisfied(
                RuleRejection.NoLineOfSight(
                    blockerAt = context.target.position,
                    blockingTerrain = Terrain.HEAVY_WOODS,
                ),
            )
        } else {
            RuleResult.Satisfied
        }
    }
}
