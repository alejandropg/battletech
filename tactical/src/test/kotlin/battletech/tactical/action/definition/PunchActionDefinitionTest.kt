package battletech.tactical.action.definition

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.aGameState
import battletech.tactical.action.aUnit
import battletech.tactical.action.rule.AdjacentRule
import battletech.tactical.action.rule.HeatPenaltyRule
import battletech.tactical.model.HexCoordinates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PunchActionDefinitionTest {

    private val definition = PunchActionDefinition()

    @Test
    fun `phase is physical attack`() {
        assertThat(definition.phase).isEqualTo(TurnPhase.PHYSICAL_ATTACK)
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
        assertThat(contexts.map { it.target?.id }).containsExactlyInAnyOrder(
            enemy1.id,
            enemy2.id,
        )
    }

    @Test
    fun `preview includes fixed punch damage`() {
        val actor = aUnit(position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(1, 0))
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            target = target,
            gameState = aGameState(),
        )

        val preview = definition.preview(context)

        assertThat(preview.expectedDamage).isEqualTo(5..5)
    }

    @Test
    fun `success chance is based on piloting skill`() {
        val actor = aUnit(pilotingSkill = 5, position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(1, 0))
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            target = target,
            gameState = aGameState(),
        )

        assertThat(definition.successChance(context)).isEqualTo(83)
    }
}
