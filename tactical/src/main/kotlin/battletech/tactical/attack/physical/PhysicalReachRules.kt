package battletech.tactical.attack.physical

import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.unitWaterDepth
import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection
import kotlin.math.abs

/** Punch reach: elevation and water-depth limits (`docs/rules/physical-attacks.md` §3). */
public class PunchReachRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult {
        val depth = unitWaterDepth(context.target.position, context.map)
        if (depth >= 2) return RuleResult.Unsatisfied(RuleRejection.TargetUnderwater(depth))

        val delta = levelOf(context.target.position, context.map) - levelOf(context.actor.position, context.map)
        if (abs(delta) > 1) return RuleResult.Unsatisfied(RuleRejection.ElevationOutOfReach(delta))
        return RuleResult.Satisfied
    }
}

/** Kick reach: elevation and water-depth limits (`docs/rules/physical-attacks.md` §3). */
public class KickReachRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult {
        val depth = unitWaterDepth(context.target.position, context.map)
        if (depth >= 1) return RuleResult.Unsatisfied(RuleRejection.TargetUnderwater(depth))

        val delta = levelOf(context.target.position, context.map) - levelOf(context.actor.position, context.map)
        if (delta !in -1..0) return RuleResult.Unsatisfied(RuleRejection.ElevationOutOfReach(delta))
        return RuleResult.Satisfied
    }
}

private fun levelOf(position: HexCoordinates, map: GameMap): Int =
    map.hexes[position]?.elevation ?: 0
