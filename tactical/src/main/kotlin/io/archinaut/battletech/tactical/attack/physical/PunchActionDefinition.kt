package io.archinaut.battletech.tactical.attack.physical

import io.archinaut.battletech.tactical.attack.AttackDefinition
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.attack.PhysicalAttackContext
import io.archinaut.battletech.tactical.attack.weapon.HeatPenaltyRule

public class PunchActionDefinition : AttackDefinition<PhysicalAttackContext> {

    override val name: String = "Punch"

    override val rules: List<AttackRule<PhysicalAttackContext>> = listOf(
        TargetAliveRule(),
        AdjacentRule(),
        PunchReachRule(),
        PunchMovementRule(),
        ProneAttackerRule(),
        HeatPenaltyRule(),
    )
}
