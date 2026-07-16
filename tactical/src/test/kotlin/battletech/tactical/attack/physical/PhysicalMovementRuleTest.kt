package battletech.tactical.attack.physical

import battletech.tactical.query.RuleResult
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.session.RuleRejection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PhysicalMovementRuleTest {

    private fun context(movement: MovementThisTurn): PhysicalAttackContext {
        val actor = aUnit(id = "actor", position = HexCoordinates(0, 0)).copy(movementThisTurn = movement)
        val target = aUnit(id = "target", position = HexCoordinates(1, 0))
        return PhysicalAttackContext(actor = actor, target = target, map = aGameState(units = listOf(actor, target)).map)
    }

    @Test
    fun `a kick is illegal after running`() {
        val result = KickMovementRule().evaluate(context(MovementThisTurn.Moved(MovementMode.RUN, 5)))
        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        assertThat((result as RuleResult.Unsatisfied).reason)
            .isInstanceOf(RuleRejection.CannotKickAfterRunningOrJumping::class.java)
    }

    @Test
    fun `a kick is illegal after jumping`() {
        val result = KickMovementRule().evaluate(context(MovementThisTurn.Moved(MovementMode.JUMP, 3)))
        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
    }

    @Test
    fun `a kick is legal after walking or standing still`() {
        assertThat(KickMovementRule().evaluate(context(MovementThisTurn.Moved(MovementMode.WALK, 2))))
            .isEqualTo(RuleResult.Satisfied)
        assertThat(KickMovementRule().evaluate(context(MovementThisTurn.Stationary)))
            .isEqualTo(RuleResult.Satisfied)
    }

    @Test
    fun `a punch is illegal after jumping`() {
        val result = PunchMovementRule().evaluate(context(MovementThisTurn.Moved(MovementMode.JUMP, 3)))
        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        assertThat((result as RuleResult.Unsatisfied).reason)
            .isInstanceOf(RuleRejection.CannotPunchAfterJumping::class.java)
    }

    @Test
    fun `a punch is legal after running`() {
        assertThat(PunchMovementRule().evaluate(context(MovementThisTurn.Moved(MovementMode.RUN, 5))))
            .isEqualTo(RuleResult.Satisfied)
    }
}
