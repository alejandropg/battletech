package battletech.tactical.action.attack.rule

import battletech.tactical.action.RuleResult
import battletech.tactical.action.attack.AttackRule
import battletech.tactical.action.attack.WeaponAttackContext
import battletech.tactical.session.RuleRejection

public class InRangeRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val weapon = context.weapon
        val distance = context.actor.position.distanceTo(context.target.position)
        return if (distance <= weapon.longRange) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(
                RuleRejection.OutOfRange(
                    weaponName = weapon.name,
                    distance = distance,
                    maxRange = weapon.longRange,
                ),
            )
        }
    }
}
