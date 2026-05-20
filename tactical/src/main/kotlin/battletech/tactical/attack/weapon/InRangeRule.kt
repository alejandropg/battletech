package battletech.tactical.attack.weapon

import battletech.tactical.query.RuleResult
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.WeaponAttackContext
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
