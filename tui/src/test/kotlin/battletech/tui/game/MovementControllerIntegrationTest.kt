package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.Unit
import battletech.tactical.action.UnitId
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MovementControllerIntegrationTest {

    private val map = GameMap(
        (0..4).flatMap { col ->
            (0..4).map { row ->
                val coords = HexCoordinates(col, row)
                coords to Hex(coords, Terrain.CLEAR)
            }
        }.toMap(),
    )

    private val unit = Unit(
        id = UnitId("atlas"),
        name = "Atlas",
        gunnerySkill = 4,
        weapons = emptyList(),
        position = HexCoordinates(2, 2),
        walkingMP = 3,
        runningMP = 5,
    )

    private val gameState = GameState(units = listOf(unit), map = map)

    @Test
    fun `enter produces reachable hexes with real action query service`() {
        val actionQueryService = ActionQueryService(listOf(MoveActionDefinition()), emptyList())
        val controller = MovementController(actionQueryService)

        val state = controller.enter(unit, gameState)

        assertTrue(state is PhaseState.Movement.Browsing)
        assertThat(state.reachability.destinations).isNotEmpty()
    }

    @Test
    fun `enter with empty movement definitions produces empty modes`() {
        val actionQueryService = ActionQueryService(emptyList(), emptyList())
        val controller = MovementController(actionQueryService)

        val state = controller.enter(unit, gameState)

        assertTrue(state.modes.isEmpty())
    }
}
