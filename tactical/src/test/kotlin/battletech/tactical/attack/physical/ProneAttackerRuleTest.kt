package battletech.tactical.attack.physical

import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.RuleResult
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.session.RuleRejection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ProneAttackerRuleTest {

    private fun context(prone: Boolean): PhysicalAttackContext {
        val actor = aUnit(id = "actor", position = HexCoordinates(0, 0)).copy(isProne = prone)
        val target = aUnit(id = "target", position = HexCoordinates(1, 0))
        return PhysicalAttackContext(actor, aGameState(units = listOf(actor, target)).map, target)
    }

    @Test
    fun `a prone attacker cannot make a physical attack`() {
        val result = ProneAttackerRule().evaluate(context(prone = true))
        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        assertThat((result as RuleResult.Unsatisfied).reason)
            .isInstanceOf(RuleRejection.AttackerProne::class.java)
    }

    @Test
    fun `a standing attacker may make a physical attack`() {
        assertThat(ProneAttackerRule().evaluate(context(prone = false))).isEqualTo(RuleResult.Satisfied)
    }
}
