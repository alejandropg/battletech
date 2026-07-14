package battletech.tactical.attack.weapon

import battletech.tactical.attack.AttackDefinition
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.WeaponAttackContext

public class FireWeaponActionDefinition : AttackDefinition<WeaponAttackContext> {

    override val name: String = "Fire Weapon"

    override val rules: List<AttackRule<WeaponAttackContext>> = listOf(
        WeaponNotDestroyedRule(),
        HasAmmoRule(),
        InRangeRule(),
        HeatPenaltyRule(),
        SubmergedWeaponRule(),
        LineOfSightRule(),
    )
}
