package battletech.tactical.action.movement

import battletech.tactical.action.aGameState
import battletech.tactical.action.aUnit
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MoveActionDefinitionTest {

    private val definition = MoveActionDefinition()

    @Test
    fun `expand returns walk and run for unit without jump`() {
        val actor = aUnit(walkingMP = 4, runningMP = 6, jumpMP = 0)
        val gameState = aGameState(units = listOf(actor))

        val contexts = definition.expand(actor, gameState)

        assertThat(contexts).hasSize(2)
        assertThat(contexts.map { it.movementMode }).containsExactly(
            MovementMode.WALK,
            MovementMode.RUN,
        )
    }

    @Test
    fun `expand returns all three modes for unit with jump`() {
        val actor = aUnit(walkingMP = 4, runningMP = 6, jumpMP = 4)
        val gameState = aGameState(units = listOf(actor))

        val contexts = definition.expand(actor, gameState)

        assertThat(contexts).hasSize(3)
        assertThat(contexts.map { it.movementMode }).containsExactly(
            MovementMode.WALK,
            MovementMode.RUN,
            MovementMode.JUMP,
        )
    }

    @Test
    fun `expand returns empty for 0 MP unit`() {
        val actor = aUnit(walkingMP = 0, runningMP = 0, jumpMP = 0)
        val gameState = aGameState(units = listOf(actor))

        val contexts = definition.expand(actor, gameState)

        assertThat(contexts).isEmpty()
    }

    @Test
    fun `preview contains reachability map`() {
        val origin = HexCoordinates(0, 0)
        val north = HexCoordinates(0, -1)
        val hexes = mapOf(
            origin to Hex(origin),
            north to Hex(north),
        )
        val actor = aUnit(position = origin, walkingMP = 2)
        val gameState = aGameState(units = listOf(actor), hexes = hexes)
        val context = MovementContext(
            actor = actor,
            movementMode = MovementMode.WALK,
            gameState = gameState,
        )

        val preview = definition.preview(context)

        assertEquals(MovementMode.WALK, preview.reachability.mode)
        assertThat(preview.reachability.destinations).isNotEmpty()
    }

    @Test
    fun `action name includes mode name`() {
        val actor = aUnit(name = "Atlas")
        val gameState = aGameState()

        val walkContext = MovementContext(actor = actor, movementMode = MovementMode.WALK, gameState = gameState)
        val runContext = MovementContext(actor = actor, movementMode = MovementMode.RUN, gameState = gameState)
        val jumpContext = MovementContext(actor = actor, movementMode = MovementMode.JUMP, gameState = gameState)

        assertEquals("Walk Atlas", definition.actionName(walkContext))
        assertEquals("Run Atlas", definition.actionName(runContext))
        assertEquals("Jump Atlas", definition.actionName(jumpContext))
    }
}
