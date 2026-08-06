package battletech.tactical.attack.weapon

import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.WeaponAttackContext
import battletech.tactical.model.unitWaterDepth
import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection

/**
 * Blocks weapon fire when the **attacker** is fully submerged and the weapon is not
 * [battletech.tactical.unit.Weapon.underwaterCapable] (`docs/rules/water.md` §2).
 *
 * All weapons in [battletech.tactical.unit.WeaponModels] default to
 * `underwaterCapable = false`, so fire is blocked entirely for submerged units unless a
 * scenario-specific weapon model explicitly opts in.
 */
public class SubmergedWeaponRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val depth = unitWaterDepth(context.actor.position, context.map)
        if (depth < 2) return RuleResult.Satisfied
        if (context.weapon.underwaterCapable) return RuleResult.Satisfied
        return RuleResult.Unsatisfied(RuleRejection.AttackerSubmerged(depth))
    }
}
