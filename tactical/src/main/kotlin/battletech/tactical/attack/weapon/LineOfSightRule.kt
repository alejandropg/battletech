package battletech.tactical.attack.weapon

import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.lineOfSight
import battletech.tactical.model.Terrain
import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection

/**
 * Blocks an attack when line of sight is obstructed: either by cumulative
 * intervening woods (≥ 3 levels: LIGHT = 1, HEAVY = 2) or by an intervening
 * hex whose elevation exceeds both endpoints.
 */
public class LineOfSightRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult {
        val los = lineOfSight(context.actor, context.target, context.gameState.map)
        return if (los.blocked) {
            RuleResult.Unsatisfied(
                RuleRejection.NoLineOfSight(
                    blockerAt = los.blockerHex ?: context.target.position,
                    blockingTerrain = los.blockingTerrain ?: Terrain.CLEAR,
                ),
            )
        } else {
            RuleResult.Satisfied
        }
    }
}
