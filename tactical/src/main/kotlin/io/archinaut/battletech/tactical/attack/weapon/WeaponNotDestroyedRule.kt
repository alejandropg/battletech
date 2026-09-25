package io.archinaut.battletech.tactical.attack.weapon

import io.archinaut.battletech.tactical.rules.RuleResult
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.attack.WeaponAttackContext
import io.archinaut.battletech.tactical.rules.RuleRejection

public class WeaponNotDestroyedRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val weapon = context.weapon
        return if (!weapon.destroyed) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(RuleRejection.WeaponDestroyed(weaponName = weapon.name))
        }
    }
}
