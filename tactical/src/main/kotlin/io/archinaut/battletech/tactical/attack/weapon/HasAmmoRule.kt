package io.archinaut.battletech.tactical.attack.weapon

import io.archinaut.battletech.tactical.rules.RuleResult
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.attack.WeaponAttackContext
import io.archinaut.battletech.tactical.rules.RuleRejection
import io.archinaut.battletech.tactical.unit.availableAmmoBins

public class HasAmmoRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val weapon = context.weapon
        val type = weapon.ammoType ?: return RuleResult.Satisfied
        val remaining = context.actor.availableAmmoBins()
            .filter { it.third.type == type }.sumOf { it.third.shots }
        return if (remaining > 0) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(RuleRejection.NoAmmo(weaponName = weapon.name))
        }
    }
}
