package io.archinaut.battletech.tactical.attack.weapon

import io.archinaut.battletech.tactical.attack.AttackContext
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.attack.lineOfSight
import io.archinaut.battletech.tactical.model.Terrain
import io.archinaut.battletech.tactical.rules.RuleResult
import io.archinaut.battletech.tactical.rules.RuleRejection

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
