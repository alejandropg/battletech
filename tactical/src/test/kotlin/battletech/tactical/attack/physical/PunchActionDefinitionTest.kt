package battletech.tactical.attack.physical

import battletech.tactical.attack.weapon.HeatPenaltyRule
import battletech.tactical.dice.twoD6AtLeastProbability
import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PunchActionDefinitionTest {

    private val definition = PunchActionDefinition()

    @Test
    fun `name is punch`() {
        assertEquals("Punch", definition.name)
    }

    @Test
    fun `rules include target alive, adjacent, reach, movement, prone and heat penalty`() {
        assertThat(definition.rules.map { it::class }).containsExactlyInAnyOrder(
            TargetAliveRule::class,
            AdjacentRule::class,
            PunchReachRule::class,
            PunchMovementRule::class,
            ProneAttackerRule::class,
            HeatPenaltyRule::class,
        )
    }

    @Test
    fun `punch damage is ceil of tonnage over ten`() {
        // Total Warfare: punch damage = ceil(tonnage / 10).
        assertEquals(5, punchDamage(aUnit(tonnage = 50)))
        assertEquals(8, punchDamage(aUnit(tonnage = 75)))
        assertEquals(3, punchDamage(aUnit(tonnage = 25)))
        assertEquals(10, punchDamage(aUnit(tonnage = 100)))
    }

    @Test
    fun `success chance is based on piloting skill`() {
        val actor = aUnit(pilotingSkill = 5, position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(1, 0))
        val gameState = aGameState(units = listOf(actor, target))

        val targetNumber = physicalToHitTargetNumber(actor, target, PhysicalAttackKind.Punch(Side.LEFT), gameState)

        assertEquals(83, twoD6AtLeastProbability(targetNumber))
    }
}
