package io.archinaut.battletech.tactical.attack.weapon

import io.archinaut.battletech.tactical.rules.RuleResult
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.attack.WeaponAttackContext
import io.archinaut.battletech.tactical.rules.RuleRejection

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
