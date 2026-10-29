package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.query.PlayerGameState
import io.archinaut.battletech.tactical.query.projectFor
import io.archinaut.battletech.tui.aGameMap
import io.archinaut.battletech.tui.aGameState
import io.archinaut.battletech.tui.aUnit
import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.input.BoardMouse
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.archinaut.tenter.widget.panel.Panel
import io.archinaut.tenter.widget.panel.PanelSet
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.widget.fixedSize
import io.archinaut.tenter.widget.panel.Panels

/**
 * Round-trips a click through the REAL frame composition: a unit is rendered onto the board
 * exactly as `RunLoop.renderFrame` composes it (status bar, board region at
 * [Workspace.STATUS_BAR_HEIGHT], panel chrome, [BoardView] content), its glyph is
 * located by scanning the resulting screen buffer, and that screen position is fed back through
 * [BoardMouse.mapContentToHex].
 *
 * Deliberately *finds* the glyph rather than computing where it ought to be: a test that derived
 * the expected position from the same constants as the production mapping would agree with it
 * while both were wrong. The conversion goes through the real panel set's hit test, which reads
 * the geometry its completed frame painted.
 */
internal class BoardClickMappingTest {

    private val marker = "ZZ"

    private fun stateWithUnitAt(hex: HexCoordinates): PlayerGameState =
        aGameState(
            units = listOf(aUnit(id = marker, position = hex)),
            map = aGameMap(cols = 10, rows = 10),
        ).projectFor(viewer = null, revealAll = true)

    /** Renders a whole frame the way `renderFrame` does and returns the screen buffer. */
    private data class Frame(
        val buffer: Canvas,
        val panels: PanelSet<GamePanelId, Unit>,
    )

    private fun renderFrame(state: PlayerGameState, width: Int = 100, height: Int = 30): Frame {
        val buffer = Canvas.offscreen(width, height)
        val (mapWidth, mapHeight) = BoardView.contentSize(state.map)
        val panels = PanelSet(
            listOf(
                Panel<GamePanelId, Unit>(
                    id = GamePanelId.BOARD,
                    title = "TACTICAL MAP",
                    normal = { BoardView(state).fixedSize(mapWidth, mapHeight) },
                ),
            ),
        )
        val boardArea = Rect(0, Workspace.STATUS_BAR_HEIGHT, width, height - Workspace.STATUS_BAR_HEIGHT)
        Panels(Unit, mapOf(GamePanelId.BOARD to boardArea)).render(buffer, panels)
        return Frame(buffer, panels)
    }

    /** The screen cell holding the first character of the [marker] glyph, or null. */
    private fun findMarker(buffer: Canvas): Pair<Int, Int>? {
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width - 1) {
                if (buffer.get(x, y).symbol == "Z" && buffer.get(x + 1, y).symbol == "Z") return x to y
            }
        }
        return null
    }

    private fun clickResolvesTo(hex: HexCoordinates) {
        val state = stateWithUnitAt(hex)
        val frame = renderFrame(state)
        val buffer = frame.buffer
        val (x, y) = findMarker(buffer) ?: error("marker glyph not rendered for $hex")

        val contentPoint = checkNotNull(frame.panels.hitTest(x, y)?.contentPoint)
        val clicked = BoardMouse.mapContentToHex(
            MouseEvent(x = x, y = y, left = true),
            contentPoint,
        )

        assertEquals(hex, clicked, "click at screen ($x,$y) — where the unit is actually drawn")
    }

    @Test
    fun `clicking the origin hex selects the origin hex`() = clickResolvesTo(HexCoordinates(0, 0))

    @Test
    fun `clicking an even column hex selects that hex`() = clickResolvesTo(HexCoordinates(2, 3))

    @Test
    fun `clicking an odd column hex selects that hex`() = clickResolvesTo(HexCoordinates(3, 2))

    @Test
    fun `clicking a hex in the first row selects it, not the row below`() =
        clickResolvesTo(HexCoordinates(4, 0))
}
