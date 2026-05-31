package battletech.tactical.attack.physical

import battletech.tactical.attack.AttackContext
import battletech.tactical.attack.AttackRule
import battletech.tactical.model.MovementMode
import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection

/** A kick may only be made after walking or standing still — not after running or jumping. */
public class KickMovementRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult =
        when (context.actor.movementThisTurn.mode) {
            MovementMode.RUN, MovementMode.JUMP ->
                RuleResult.Unsatisfied(RuleRejection.CannotKickAfterRunningOrJumping)
            else -> RuleResult.Satisfied
        }
}

/** A punch may not be made after jumping (a jump only permits a death-from-above). */
public class PunchMovementRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult =
        if (context.actor.movementThisTurn.mode == MovementMode.JUMP) {
            RuleResult.Unsatisfied(RuleRejection.CannotPunchAfterJumping)
        } else {
            RuleResult.Satisfied
        }
}

/** A prone unit cannot make any physical attack. */
public class ProneAttackerRule : AttackRule<AttackContext> {
    override fun evaluate(context: AttackContext): RuleResult =
        if (context.actor.isProne) RuleResult.Unsatisfied(RuleRejection.AttackerProne) else RuleResult.Satisfied
}
