package io.archinaut.battletech.tactical.attack.physical

import io.archinaut.battletech.tactical.attack.AttackDefinition
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.attack.PhysicalAttackContext
import io.archinaut.battletech.tactical.attack.weapon.HeatPenaltyRule

public class KickActionDefinition : AttackDefinition<PhysicalAttackContext> {

    override val name: String = "Kick"

    override val rules: List<AttackRule<PhysicalAttackContext>> = listOf(
        TargetAliveRule(),
        AdjacentRule(),
        KickReachRule(),
        KickMovementRule(),
        ProneAttackerRule(),
        HeatPenaltyRule(),
    )
}
