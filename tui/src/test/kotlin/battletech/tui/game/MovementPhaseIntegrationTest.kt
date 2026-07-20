package battletech.tui.game

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tactical.movement.MovementRules
import battletech.tactical.unit.UnitRoster
import battletech.tui.aUnit
import battletech.tui.anAppState
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.enterBrowsing
import battletech.tui.viewFor
import com.github.ajalt.mordant.input.KeyboardEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    private val gameState = GameState(units = UnitRoster(listOf(unit)), map = map)

    @Test
    fun `enter produces reachable hexes via PlayerView`() {
        val view = viewFor(unit.owner, gameState)

        val phase = enterBrowsing(unit, view)

        assertInstanceOf(MovementPhase.Browsing::class.java, phase)
        assertThat(phase.reachability.destinations).isNotEmpty()
    }

    @Test
    fun `hovering and confirming on the unit's own hex offers the current facing to stay put`() {
        val view = viewFor(unit.owner, gameState)
        val phase = enterBrowsing(unit, view)
        // Start one hex north of the unit and move south, landing on the unit's own hex —
        // this is the real ReachabilityCalculator output, no hand-built fixtures.
        val startCursor = unit.position.neighbor(HexDirection.N)
        val state = anAppState(phase = phase, cursor = startCursor, gameState = gameState)

        val hovered = phase.handle(KeyboardEvent("ArrowDown"), state)
        assertNotNull(hovered)
        val hoveredBrowsing = hovered!!.app.phase as MovementPhase.Browsing
        assertEquals(MovementRules.stationaryHex(unit), hoveredBrowsing.hoveredDestination)

        val confirmed = hoveredBrowsing.handle(KeyboardEvent("Enter"), hovered.app)
        assertNotNull(confirmed)
        val facingPhase = assertInstanceOf(MovementPhase.SelectingFacing::class.java, confirmed!!.app.phase)
        assertTrue(facingPhase.options.any { it.facing == unit.facing && it.mpSpent == 0 && it.path.isEmpty() })
    }

    @Test
    fun `pressing the current facing's number on the unit's own hex stays put on RUN`() {
        val view = viewFor(unit.owner, gameState)
        val browsing = enterBrowsing(unit, view).copy(currentModeIndex = 1)
        val startCursor = unit.position.neighbor(HexDirection.N)
        val state = anAppState(phase = browsing, cursor = startCursor, gameState = gameState)

        val hovered = browsing.handle(KeyboardEvent("ArrowDown"), state)
        assertNotNull(hovered)
        val hoveredBrowsing = hovered!!.app.phase as MovementPhase.Browsing

        // "1" is FACING_ORDER[0] == N, the unit's current facing.
        val result = hoveredBrowsing.handle(KeyboardEvent("1"), hovered.app)

        assertNotNull(result)
        val movedUnit = result!!.app.visibleState.units.first { it.id == unit.id }
        assertEquals(unit.position, movedUnit.position)
        assertEquals(unit.facing, movedUnit.facing)
    }

    @Test
    fun `confirming on the unit's own hex on JUMP commits the stationary hex directly (no facing choice)`() {
        val jumpUnit = aUnit(id = "jumper", position = HexCoordinates(2, 2), jumpMP = 3)
        val jumpGameState = GameState(units = UnitRoster(listOf(jumpUnit)), map = map)
        val view = viewFor(jumpUnit.owner, jumpGameState)
        val browsing = enterBrowsing(jumpUnit, view)
        assertEquals(MovementMode.JUMP, browsing.reachability.mode)
        val startCursor = jumpUnit.position.neighbor(HexDirection.N)
        val state = anAppState(phase = browsing, cursor = startCursor, gameState = jumpGameState)

        val hovered = browsing.handle(KeyboardEvent("ArrowDown"), state)
        assertNotNull(hovered)
        val hoveredBrowsing = hovered!!.app.phase as MovementPhase.Browsing
        assertEquals(MovementRules.stationaryHex(jumpUnit), hoveredBrowsing.hoveredDestination)

        val result = hoveredBrowsing.handle(KeyboardEvent("Enter"), hovered.app)

        assertNotNull(result)
        val movedUnit = result!!.app.visibleState.units.first { it.id == jumpUnit.id }
        assertEquals(jumpUnit.position, movedUnit.position)
        assertEquals(jumpUnit.facing, movedUnit.facing)
    }
}
