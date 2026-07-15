package battletech.tactical.attack.physical

import battletech.tactical.attack.weapon.HeatPenaltyRule
import battletech.tactical.dice.twoD6AtLeastProbability
import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import battletech.tactical.query.OwnUnit

internal class KickActionDefinitionTest {

    private val definition = KickActionDefinition()

    @Test
    fun `name is kick`() {
        assertEquals("Kick", definition.name)
    }

    @Test
    fun `rules include target alive, adjacent and heat penalty`() {
        assertThat(definition.rules.map { it::class }).contains(
            TargetAliveRule::class,
            AdjacentRule::class,
            HeatPenaltyRule::class,
        )
    }

    @Test
    fun `kick damage is ceil of tonnage over five`() {
        // Total Warfare: kick damage = ceil(tonnage / 5).
        assertEquals(10, kickDamage(aUnit(tonnage = 50)))
        assertEquals(15, kickDamage(aUnit(tonnage = 75)))
        assertEquals(5, kickDamage(aUnit(tonnage = 25)))
        assertEquals(20, kickDamage(aUnit(tonnage = 100)))
    }

    @Test
    fun `success chance reflects the minus two kick modifier`() {
        // Kick is easier than a punch: piloting skill - 2 target number.
        // Piloting 5 -> target number 3 -> 97% on 2d6.
        val actor = aUnit(pilotingSkill = 5, position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(1, 0))
        val gameState = aGameState(units = listOf(actor, target))

        val targetNumber = physicalToHitTargetNumber(actor, OwnUnit(target), PhysicalAttackKind.Kick(Side.LEFT), gameState.map)

        assertEquals(97, twoD6AtLeastProbability(targetNumber))
    }
}
