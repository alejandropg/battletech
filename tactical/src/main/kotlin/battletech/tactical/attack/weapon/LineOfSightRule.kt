package battletech.tactical.attack.weapon

import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.lineOfSight
import battletech.tactical.model.Terrain
import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection

/** Blocks an attack when line of sight is obstructed (`docs/rules/line-of-sight.md` §1–2). */
public class LineOfSightRule : AttackRule<AttackContext> {

    override fun evaluate(context: AttackContext): RuleResult {
        val los = lineOfSight(context.actor.position, context.target.position, context.map)
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
