package battletech.tactical.attack.weapon

import battletech.tactical.query.RuleResult
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.WeaponAttackContext
import battletech.tactical.session.RuleRejection

public class HasAmmoRule : AttackRule<WeaponAttackContext> {

    override fun evaluate(context: WeaponAttackContext): RuleResult {
        val weapon = context.weapon
        val type = weapon.ammoType ?: return RuleResult.Satisfied
        val remaining = context.actor.criticalLayout.ammoBins()
            .filter { it.third.type == type }.sumOf { it.third.shots }
        return if (remaining > 0) {
            RuleResult.Satisfied
        } else {
            RuleResult.Unsatisfied(RuleRejection.NoAmmo(weaponName = weapon.name))
        }
    }
}
