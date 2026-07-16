package battletech.tactical.attack.physical

import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.RuleResult
import battletech.tactical.query.aUnit
import battletech.tactical.session.RuleRejection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PhysicalReachRuleTest {

    private val attackerPos = HexCoordinates(0, 0)
    private val targetPos = HexCoordinates(1, 0)

    private fun context(
        attackerElevation: Int = 0,
        targetElevation: Int = 0,
        targetDepth: Int = 0,
    ): PhysicalAttackContext {
        val actor = aUnit(id = "actor", position = attackerPos)
        val target = aUnit(id = "target", position = targetPos)
        val hexes = mapOf(
            attackerPos to Hex(attackerPos, elevation = attackerElevation),
            targetPos to Hex(targetPos, elevation = targetElevation, depth = targetDepth),
        )
        return PhysicalAttackContext(actor, GameMap(hexes), target)
    }

    private fun reason(result: RuleResult): RuleRejection =
        (result as RuleResult.Unsatisfied).reason

    // --- Punch elevation: legal within +/-1 level ---

    @Test
    fun `punch is legal at the same level and within one level`() {
        assertThat(PunchReachRule().evaluate(context(targetElevation = 0))).isEqualTo(RuleResult.Satisfied)
        assertThat(PunchReachRule().evaluate(context(targetElevation = 1))).isEqualTo(RuleResult.Satisfied)
        assertThat(PunchReachRule().evaluate(context(attackerElevation = 1, targetElevation = 0)))
            .isEqualTo(RuleResult.Satisfied)
    }

    @Test
    fun `punch is illegal more than one level apart`() {
        val result = PunchReachRule().evaluate(context(targetElevation = 2))
        assertThat(reason(result)).isInstanceOf(RuleRejection.ElevationOutOfReach::class.java)
    }

    // --- Kick elevation: legal at same level or one lower, never higher ---

    @Test
    fun `kick is legal at the same level or one lower`() {
        assertThat(KickReachRule().evaluate(context(targetElevation = 0))).isEqualTo(RuleResult.Satisfied)
        assertThat(KickReachRule().evaluate(context(attackerElevation = 1, targetElevation = 0)))
            .isEqualTo(RuleResult.Satisfied)
    }

    @Test
    fun `kick is illegal against a higher target`() {
        val result = KickReachRule().evaluate(context(targetElevation = 1))
        assertThat(reason(result)).isInstanceOf(RuleRejection.ElevationOutOfReach::class.java)
    }

    // --- Water ---

    @Test
    fun `kick is illegal when the target's legs are submerged`() {
        val result = KickReachRule().evaluate(context(targetDepth = 1))
        assertThat(reason(result)).isInstanceOf(RuleRejection.TargetUnderwater::class.java)
    }

    @Test
    fun `punch is legal over shallow water but illegal when the target is fully submerged`() {
        assertThat(PunchReachRule().evaluate(context(targetDepth = 1))).isEqualTo(RuleResult.Satisfied)
        val deep = PunchReachRule().evaluate(context(targetDepth = 2))
        assertThat(reason(deep)).isInstanceOf(RuleRejection.TargetUnderwater::class.java)
    }
}
