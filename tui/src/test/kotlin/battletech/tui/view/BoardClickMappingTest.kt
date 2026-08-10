package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.PlayerGameState
import battletech.tactical.query.projectFor
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.game.phase.BOARD_ORIGIN_X
import battletech.tui.game.phase.BOARD_ORIGIN_Y
import battletech.tui.input.InputMapper
import battletech.tui.screen.Canvas
import battletech.tui.screen.ScreenBuffer
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Round-trips a click through the REAL frame composition: a unit is rendered onto the board
 * exactly as `RunLoop.renderFrame` composes it (status bar, board region at
 * [Workspace.STATUS_BAR_HEIGHT], [scrollingPanel] chrome, [BoardView] content), its glyph is
 * located by scanning the resulting screen buffer, and that screen position is fed back through
 * [InputMapper.mapMouseToHex].
 *
 * Deliberately *finds* the glyph rather than computing where it ought to be: a test that derived
 * the expected position from the same constants as the production mapping would agree with it
 * while both were wrong, which is exactly how the status-bar offset went unnoticed — every click
 * resolved one hex row low because [BOARD_ORIGIN_Y] ignored the status bar.
 */
internal class BoardClickMappingTest {

    private val marker = "ZZ"

    private fun stateWithUnitAt(hex: HexCoordinates): PlayerGameState =
        aGameState(
            units = listOf(aUnit(id = marker, position = hex)),
            map = aGameMap(cols = 10, rows = 10),
        ).projectFor(viewer = null, revealAll = true)

    /** Renders a whole frame the way `renderFrame` does and returns the screen buffer. */
    private fun renderFrame(state: PlayerGameState, width: Int = 100, height: Int = 30): ScreenBuffer {
        val buffer = ScreenBuffer(width, height)
        val screen = Canvas.of(buffer)
        val boardHeight = height - Workspace.STATUS_BAR_HEIGHT
        val (mapWidth, mapHeight) = BoardView.contentSize(state.map)
        val board = scrollingPanel(
            title = "TACTICAL MAP",
            badge = null,
            content = BoardView(state),
            extent = ContentExtent.Fixed(mapWidth, mapHeight),
            offset = ScrollOffset.ZERO,
        )
        board.render(screen.region(0, Workspace.STATUS_BAR_HEIGHT, width, boardHeight))
        return buffer
    }

    /** The screen cell holding the first character of the [marker] glyph, or null. */
    private fun findMarker(buffer: ScreenBuffer): Pair<Int, Int>? {
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width - 1) {
                if (buffer.get(x, y).char == "Z" && buffer.get(x + 1, y).char == "Z") return x to y
            }
        }
        return null
    }

    private fun clickResolvesTo(hex: HexCoordinates) {
        val state = stateWithUnitAt(hex)
        val buffer = renderFrame(state)
        val (x, y) = findMarker(buffer) ?: error("marker glyph not rendered for $hex")

        val clicked = InputMapper.mapMouseToHex(
            MouseEvent(x = x, y = y, left = true),
            boardX = BOARD_ORIGIN_X,
            boardY = BOARD_ORIGIN_Y,
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
