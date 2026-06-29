package battletech.tactical.attack.physical

import battletech.tactical.attack.aPhysicalAttackContext
import battletech.tactical.query.RuleResult
import battletech.tactical.query.aUnit
import battletech.tactical.session.RuleRejection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TargetAliveRuleTest {

    private val rule = TargetAliveRule()

    @Test
    fun `satisfied when target is alive`() {
        val target = aUnit(id = "target", isDestroyed = false)
        val result = rule.evaluate(aPhysicalAttackContext(target = target))
        assertThat(result).isEqualTo(RuleResult.Satisfied)
    }

    @Test
    fun `unsatisfied with TargetDestroyed when target is destroyed`() {
        val target = aUnit(id = "target", isDestroyed = true)
        val result = rule.evaluate(aPhysicalAttackContext(target = target))
        assertThat(result).isInstanceOf(RuleResult.Unsatisfied::class.java)
        val unsatisfied = result as RuleResult.Unsatisfied
        assertThat(unsatisfied.reason).isEqualTo(RuleRejection.TargetDestroyed)
    }
}
