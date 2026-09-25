package io.archinaut.battletech.tactical.attack.weapon

import io.archinaut.battletech.tactical.attack.AttackDefinition
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.attack.WeaponAttackContext

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
