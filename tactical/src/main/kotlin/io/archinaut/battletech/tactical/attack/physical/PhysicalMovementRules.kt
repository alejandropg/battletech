package io.archinaut.battletech.tactical.attack.physical

import io.archinaut.battletech.tactical.attack.AttackContext
import io.archinaut.battletech.tactical.attack.AttackRule
import io.archinaut.battletech.tactical.model.MovementMode
import io.archinaut.battletech.tactical.rules.RuleResult
import io.archinaut.battletech.tactical.rules.RuleRejection
import io.archinaut.battletech.tactical.unit.MovementThisTurn

/** Movement restriction on kicking (`docs/rules/physical-attacks.md` §2). */
public class KickMovementRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult {
        val movement = context.actor.movementThisTurn
        val ranOrJumped = movement is MovementThisTurn.Moved &&
            (movement.mode == MovementMode.RUN || movement.mode == MovementMode.JUMP)
        return if (ranOrJumped) {
            RuleResult.Unsatisfied(RuleRejection.CannotKickAfterRunningOrJumping)
        } else {
            RuleResult.Satisfied
        }
    }
}

/** Movement restriction on punching (`docs/rules/physical-attacks.md` §2). */
public class PunchMovementRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult {
        val movement = context.actor.movementThisTurn
        return if (movement is MovementThisTurn.Moved && movement.mode == MovementMode.JUMP) {
            RuleResult.Unsatisfied(RuleRejection.CannotPunchAfterJumping)
        } else {
            RuleResult.Satisfied
        }
    }
}

/** A prone unit cannot make any physical attack (`docs/rules/physical-attacks.md` §1). */
public class ProneAttackerRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult =
        if (context.actor.isProne) RuleResult.Unsatisfied(RuleRejection.AttackerProne) else RuleResult.Satisfied
}
