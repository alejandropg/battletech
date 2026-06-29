package battletech.tactical.attack.physical

import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.attack.weapon.HeatPenaltyRule
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KickActionDefinitionTest {

    private val definition = KickActionDefinition()

    @Test
    fun `phase is physical attack`() {
        assertEquals(TurnPhase.PHYSICAL_ATTACK, definition.phase)
    }

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
    fun `preview kick damage is ceil of tonnage over five`() {
        // Total Warfare: kick damage = ceil(tonnage / 5).
        val target = aUnit(id = "enemy", position = HexCoordinates(1, 0))

        fun previewFor(tonnage: Int): PhysicalAttackPreview {
            val actor = aUnit(tonnage = tonnage, position = HexCoordinates(0, 0))
            val context = PhysicalAttackContext(actor = actor, target = target, gameState = aGameState())
            return definition.preview(context) as PhysicalAttackPreview
        }

        assertEquals(10..10, previewFor(50).expectedDamage)
        assertEquals(15..15, previewFor(75).expectedDamage)
        assertEquals(5..5, previewFor(25).expectedDamage)
        assertEquals(20..20, previewFor(100).expectedDamage)
    }

    @Test
    fun `success chance reflects the minus two kick modifier`() {
        // Kick is easier than a punch: piloting skill - 2 target number.
        // Piloting 5 -> target number 3 -> 97% on 2d6.
        val actor = aUnit(pilotingSkill = 5, position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(1, 0))
        val context = PhysicalAttackContext(actor = actor, target = target, gameState = aGameState())

        assertEquals(97, definition.successChance(context))
    }
}
