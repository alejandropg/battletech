package battletech.tactical.attack.physical

import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.unitWaterDepth
import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection
import kotlin.math.abs

/**
 * Punch reach: the target must be within one level of the attacker and must
 * not be fully submerged (depth ≥ 2 puts the upper body underwater).
 */
public class PunchReachRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult {
        val depth = unitWaterDepth(context.target.position, context.map)
        if (depth >= 2) return RuleResult.Unsatisfied(RuleRejection.TargetUnderwater(depth))

        val delta = levelOf(context.target.position, context.map) - levelOf(context.actor.position, context.map)
        if (abs(delta) > 1) return RuleResult.Unsatisfied(RuleRejection.ElevationOutOfReach(delta))
        return RuleResult.Satisfied
    }
}

/**
 * Kick reach: the target must be at the attacker's level or one level lower
 * (never higher), and its legs must not be submerged (depth ≥ 1).
 */
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
