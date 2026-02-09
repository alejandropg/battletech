package battletech.tactical.action.definition

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.aGameState
import battletech.tactical.action.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MoveActionDefinitionTest {

    private val definition = MoveActionDefinition()

    @Test
    fun `phase is movement`() {
        assertThat(definition.phase).isEqualTo(TurnPhase.MOVEMENT)
    }

    @Test
    fun `expand returns single context`() {
        val actor = aUnit()
        val gameState = aGameState(units = listOf(actor))

        val contexts = definition.expand(actor, gameState)

        assertThat(contexts).hasSize(1)
        assertThat(contexts[0].actor).isEqualTo(actor)
    }

    @Test
    fun `success chance is always 100 percent`() {
        val actor = aUnit()
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            gameState = aGameState(),
        )

        assertThat(definition.successChance(context)).isEqualTo(100)
    }

    @Test
    fun `preview is empty`() {
        val actor = aUnit()
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            gameState = aGameState(),
        )

        val preview = definition.preview(context)

        assertThat(preview.expectedDamage).isNull()
        assertThat(preview.heatGenerated).isNull()
        assertThat(preview.ammoConsumed).isNull()
    }

    @Test
    fun `has no rules`() {
        assertThat(definition.rules).isEmpty()
    }
}
