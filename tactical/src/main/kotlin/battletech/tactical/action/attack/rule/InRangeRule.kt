package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnavailabilityReason
import battletech.tactical.action.attack.AttackRule
import battletech.tactical.action.attack.WeaponAttackContext

public class InRangeRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val weapon = context.weapon
        val distance = context.actor.position.distanceTo(context.target.position)
        return if (distance <= weapon.longRange) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(
                UnavailabilityReason(
                    code = "OUT_OF_RANGE",
                    description = "${weapon.name} target is at distance $distance, max range is ${weapon.longRange}",
                )
            )
        }
    }
}
