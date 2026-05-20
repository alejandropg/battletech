package battletech.tui.game

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import battletech.tactical.query.DefaultPlayerView
import battletech.tui.aUnit
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.enterBrowsing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class MovementPhaseIntegrationTest {

    private val map = GameMap(
        (0..4).flatMap { col ->
            (0..4).map { row ->
                val coords = HexCoordinates(col, row)
                coords to Hex(coords, Terrain.CLEAR)
            }
        }.toMap(),
    )

    private val unit = aUnit(position = HexCoordinates(2, 2), walkingMP = 3, runningMP = 5)
    private val gameState = GameState(units = listOf(unit), map = map)

    @Test
    fun `enter produces reachable hexes via PlayerView`() {
        val view = DefaultPlayerView(unit.owner, gameState)

        val phase = enterBrowsing(unit, view)

        assertInstanceOf(MovementPhase.Browsing::class.java, phase)
        assertThat(phase.reachability.destinations).isNotEmpty()
    }
}
