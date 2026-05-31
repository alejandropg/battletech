package battletech.tactical.attack.physical

import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
import battletech.tactical.model.GameState
import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection
import battletech.tactical.unit.CombatUnit
import kotlin.math.abs

/**
 * Punch reach: the target must be within one level of the attacker and must
 * not be fully submerged (depth ≥ 2 puts the upper body underwater).
 */
public class PunchReachRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult {
        val depth = waterDepth(context.target, context.gameState)
        if (depth >= 2) return RuleResult.Unsatisfied(RuleRejection.TargetUnderwater(depth))

        val delta = levelOf(context.target, context.gameState) - levelOf(context.actor, context.gameState)
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
        val depth = waterDepth(context.target, context.gameState)
        if (depth >= 1) return RuleResult.Unsatisfied(RuleRejection.TargetUnderwater(depth))

        val delta = levelOf(context.target, context.gameState) - levelOf(context.actor, context.gameState)
        if (delta !in -1..0) return RuleResult.Unsatisfied(RuleRejection.ElevationOutOfReach(delta))
        return RuleResult.Satisfied
    }
}

private fun levelOf(unit: CombatUnit, gameState: GameState): Int =
    gameState.map.hexes[unit.position]?.elevation ?: 0

private fun waterDepth(unit: CombatUnit, gameState: GameState): Int =
    gameState.map.hexes[unit.position]?.depth ?: 0
