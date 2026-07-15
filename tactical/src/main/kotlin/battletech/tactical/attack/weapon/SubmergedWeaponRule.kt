package battletech.tactical.attack.weapon

import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.WeaponAttackContext
import battletech.tactical.model.unitWaterDepth
import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection

/**
 * Blocks weapon fire when the **attacker** is fully submerged (water depth ≥ 2) and
 * the weapon is not [battletech.tactical.unit.Weapon.underwaterCapable].
 *
 * Standard BattleTech: a BattleMech standing in depth-2+ water cannot fire any of its
 * standard surface weapons — the targeting systems, barrels, and heat exchangers are all
 * flooded. All weapons in [battletech.tactical.unit.WeaponModels] default to
 * `underwaterCapable = false`, blocking fire entirely for submerged units unless a
 * scenario-specific weapon model explicitly opts in.
 *
 * Depth-1 water does NOT block weapons (the upper body and weapon systems remain above
 * the waterline).
 */
public class SubmergedWeaponRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val depth = unitWaterDepth(context.actor.position, context.map)
        if (depth < 2) return RuleResult.Satisfied
        if (context.weapon.underwaterCapable) return RuleResult.Satisfied
        return RuleResult.Unsatisfied(RuleRejection.AttackerSubmerged(depth))
    }
}
