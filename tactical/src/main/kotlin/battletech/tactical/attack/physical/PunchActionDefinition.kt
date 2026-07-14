package battletech.tactical.attack.physical

import battletech.tactical.attack.AttackDefinition
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.attack.weapon.HeatPenaltyRule

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
