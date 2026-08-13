package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.projectFor
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.hex.HexGeometry
import battletech.tui.hex.HexHighlight
import battletech.tui.hex.HexLayout
import battletech.tui.hex.destroyedIcon
import battletech.tui.screen.BoardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.FocusRect
import tenter.screen.ScreenBuffer
import tenter.screen.UiRole
import tenter.view.Scrolled
import tenter.view.line
import tenter.view.render

/**
 * [BoardView] is now plain content — no chrome, no scroll, no clipping; that's all
 * [Scrolled]'s job (see `ScrolledTest` and `RunLoop`'s composition of the two). Every
 * coordinate here is therefore the unscrolled position [HexLayout.hexToScreen] gives a hex,
 * shifted by [BoardView.MAP_ORIGIN_X]/[BoardView.MAP_ORIGIN_Y] for the coordinate-label margins.
 */
internal class BoardViewTest {

    @Test
    fun `renders hex borders for a 3x3 map`() {
        val state = aGameState(map = aGameMap(cols = 3, rows = 3)).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state)
        val buffer = render(view, 32, 18)

        // Hex at (0,0): '/' at charX=4, charY=1+2
        assertEquals("/", buffer.get(4, 3).char)
        // Hex at (1,0): '/' at charX=4+7+1, charY=1+2+1
        assertEquals("/", buffer.get(12, 4).char)
        // Hex at (2,0): '/' at charX=4+14, charY=1+2
        assertEquals("/", buffer.get(18, 3).char)
    }

    @Test
    fun `renders one-based coordinates on every map edge`() {
        val state = aGameState(map = aGameMap(cols = 3, rows = 3)).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state)
        val buffer = render(view, 32, 18)

        val mapWidth = 2 * HexGeometry.COL_STRIDE + HexGeometry.HEX_WIDTH
        val mapHeight = 2 * HexGeometry.ROW_STRIDE + HexGeometry.ODD_COL_ROW_OFFSET + HexGeometry.HEX_HEIGHT
        val coordinateLabels = listOf("01", "02", "03")
        val columnLabelStarts = listOf(7, 14, 21)
        val bottomLabelY = BoardView.MAP_ORIGIN_Y + mapHeight + BoardView.BOTTOM_LABEL_GAP
        for ((index, x) in columnLabelStarts.withIndex()) {
            assertEquals(coordinateLabels[index], buffer.line(0, x, 2))
            assertEquals(coordinateLabels[index], buffer.line(bottomLabelY, x, 2))
        }

        val rowCenters = listOf(3, 7, 11)
        val rightLabelX = BoardView.MAP_ORIGIN_X + mapWidth + 2
        for ((index, y) in rowCenters.withIndex()) {
            assertEquals(coordinateLabels[index], buffer.line(y, 0, 2))
            assertEquals(coordinateLabels[index], buffer.line(y, rightLabelX, 2))
        }

        assertEquals(UiRole.TEXT_SUBTLE, buffer.get(columnLabelStarts[0], 0).style.fg)
        assertEquals(UiRole.TEXT_SUBTLE, buffer.get(0, rowCenters[0]).style.fg)

        assertEquals(" ", buffer.get(BoardView.MAP_ORIGIN_X - 1, rowCenters[0]).char)
        assertEquals(" ", buffer.get(BoardView.MAP_ORIGIN_X - 2, rowCenters[0]).char)
        assertEquals(" ", buffer.get(BoardView.MAP_ORIGIN_X + mapWidth, rowCenters[0]).char)
        assertEquals(" ", buffer.get(BoardView.MAP_ORIGIN_X + mapWidth + 1, rowCenters[0]).char)
        assertEquals(" ", buffer.get(columnLabelStarts[0], BoardView.MAP_ORIGIN_Y + mapHeight).char)
    }

    @Test
    fun `renders unit id on hex`() {
        val unit = aUnit(id = "A1", name = "Atlas", position = HexCoordinates(0, 0))
        val state = aGameState(units = listOf(unit), map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state)
        val buffer = render(view, 32, 18)

        // Unit id "A1" at hex center: charX=4+4, charY=1+3 for north-facing units
        assertEquals("A", buffer.get(8, 4).char)
        assertEquals("1", buffer.get(9, 4).char)
    }

    @Test
    fun `marks the cursor hex as focus, for the enclosing Scrolled to auto-follow`() {
        val state = aGameState(map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val cursor = HexCoordinates(1, 1)
        val view = BoardView(state, cursorPosition = cursor)
        val canvas = Canvas.of(ScreenBuffer(32, 18))

        view.render(canvas)

        val (x, y) = HexLayout.hexToScreen(cursor.col, cursor.row)
        assertEquals(
            FocusRect(
                BoardView.MAP_ORIGIN_X + x,
                BoardView.MAP_ORIGIN_Y + y,
                HexGeometry.HEX_WIDTH,
                HexGeometry.HEX_HEIGHT,
            ),
            canvas.focusRect(),
        )
    }

    @Test
    fun `no cursor means no focus is marked`() {
        val state = aGameState(map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state, cursorPosition = null)
        val canvas = Canvas.of(ScreenBuffer(32, 18))

        view.render(canvas)

        assertEquals(null, canvas.focusRect())
    }

    @Test
    fun `contentSize spans every hex in the map, including the odd-column row offset`() {
        val map = aGameMap(cols = 3, rows = 3)

        val (width, height) = BoardView.contentSize(map)

        // maxCol = 2, maxRow = 2
        assertEquals(
            2 * HexGeometry.COL_STRIDE + HexGeometry.HEX_WIDTH + 2 * BoardView.MAP_ORIGIN_X,
            width,
        )
        assertEquals(
            2 * HexGeometry.ROW_STRIDE + HexGeometry.ODD_COL_ROW_OFFSET + HexGeometry.HEX_HEIGHT +
                BoardView.MAP_ORIGIN_Y + BoardView.BOTTOM_LABEL_GAP + 1,
            height,
        )
    }

    @Test
    fun `cursor position highlights hex`() {
        val state = aGameState(map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val cursor = HexCoordinates(1, 1)
        val view = BoardView(state, cursorPosition = cursor)
        val buffer = render(view, 30, 18)

        // Hex at (1,1) border '/' offset by +1,+1 within the hex glyph (row1)
        assertEquals(BoardRole.BOARD_ACTIVE, buffer.get(12, 8).style.fg)
    }

    @Test
    fun `renders destroyed unit with its id and a skull marker`() {
        val unit = aUnit(id = "A1", name = "Atlas", position = HexCoordinates(0, 0)).copy(isDestroyed = true)
        val state = aGameState(units = listOf(unit), map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state)
        val buffer = render(view, 32, 18)

        // Unit id "A1" at hex center: charX=8/9, charY=4 (same cells as the id in the
        // "renders unit id on hex" test above), with a skull marker left of the id at charX=7.
        assertEquals("A", buffer.get(8, 4).char)
        assertEquals("1", buffer.get(9, 4).char)
        assertEquals(BoardRole.DESTROYED, buffer.get(9, 4).style.fg)
        assertEquals(destroyedIcon(), buffer.get(7, 4).char)
        assertEquals(BoardRole.DESTROYED, buffer.get(7, 4).style.fg)
    }

    @Test
    fun `prone unit still renders its lowercase id, distinct from destroyed`() {
        val unit = aUnit(id = "A1", name = "Atlas", position = HexCoordinates(0, 0)).copy(isProne = true)
        val state = aGameState(units = listOf(unit), map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state)
        val buffer = render(view, 32, 18)

        assertEquals("a", buffer.get(8, 4).char)
        assertEquals("1", buffer.get(9, 4).char)
    }

    @Test
    fun `highlights map with reachable and path overlays`() {
        val state = aGameState(map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val highlights = mapOf(
            HexCoordinates(1, 0) to HexHighlight.REACHABLE_WALK,
            HexCoordinates(2, 0) to HexHighlight.PATH,
        )
        val view = BoardView(state, hexHighlights = highlights)
        val buffer = render(view, 32, 18)

        // Hex center is at x+4, y+2 from hex render origin
        assertEquals(".", buffer.get(15, 5).char)
        assertEquals("*", buffer.get(22, 3).char)
    }
}
