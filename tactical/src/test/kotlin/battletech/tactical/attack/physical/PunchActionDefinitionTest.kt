package battletech.tactical.attack.physical

import battletech.tactical.model.TurnPhase
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.attack.physical.PhysicalAttackPreview
import battletech.tactical.attack.physical.AdjacentRule
import battletech.tactical.attack.weapon.HeatPenaltyRule
import battletech.tactical.model.HexCoordinates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PunchActionDefinitionTest {

    private val definition = PunchActionDefinition()

    @Test
    fun `phase is physical attack`() {
        assertEquals(TurnPhase.PHYSICAL_ATTACK, definition.phase)
    }

    @Test
    fun `rules include adjacent and heat penalty`() {
        assertThat(definition.rules).hasSize(2)
        assertThat(definition.rules.map { it::class }).containsExactlyInAnyOrder(
            AdjacentRule::class,
            HeatPenaltyRule::class,
        )
    }

    @Test
    fun `expand creates one context per enemy`() {
        val actor = aUnit(position = HexCoordinates(0, 0))
        val enemy1 = aUnit(id = "enemy-1", position = HexCoordinates(1, 0))
        val enemy2 = aUnit(id = "enemy-2", position = HexCoordinates(2, 0))
        val gameState = aGameState(units = listOf(actor, enemy1, enemy2))

        val contexts = definition.expand(actor, gameState)

        assertThat(contexts).hasSize(2)
        assertThat(contexts.map { it.target.id }).containsExactlyInAnyOrder(
            enemy1.id,
            enemy2.id,
        )
    }

    @Test
    fun `preview punch damage is ceil of tonnage over ten`() {
        // Total Warfare: punch damage = ceil(tonnage / 10).
        val target = aUnit(id = "enemy", position = HexCoordinates(1, 0))

        fun previewFor(tonnage: Int): PhysicalAttackPreview {
            val actor = aUnit(tonnage = tonnage, position = HexCoordinates(0, 0))
            val context = PhysicalAttackContext(actor = actor, target = target, gameState = aGameState())
            return definition.preview(context) as PhysicalAttackPreview
        }

        assertEquals(5..5, previewFor(50).expectedDamage)
        assertEquals(8..8, previewFor(75).expectedDamage)
        assertEquals(3..3, previewFor(25).expectedDamage)
        assertEquals(10..10, previewFor(100).expectedDamage)
    }

    @Test
    fun `success chance is based on piloting skill`() {
        val actor = aUnit(pilotingSkill = 5, position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(1, 0))
        val context = PhysicalAttackContext(
            actor = actor,
            target = target,
            gameState = aGameState(),
        )

        assertEquals(83, definition.successChance(context))
    }
}
