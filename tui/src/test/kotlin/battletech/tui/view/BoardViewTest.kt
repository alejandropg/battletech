package battletech.tui.view

import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.hex.HexGeometry
import battletech.tui.hex.HexHighlight
import battletech.tui.hex.HexLayout
import battletech.tui.hex.destroyedIcon
import battletech.tui.screen.Canvas
import battletech.tui.screen.Color
import battletech.tui.screen.FocusRect
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.projectFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [BoardView] is now plain content — no chrome, no scroll, no clipping; that's all
 * [ScrollableView]'s job (see `ScrollableViewTest` and `RunLoop`'s composition of the two). Every
 * coordinate here is therefore the RAW, unscrolled position [HexLayout.hexToScreen] gives a hex,
 * with no border/padding offset added.
 */
internal class BoardViewTest {

    @Test
    fun `renders hex borders for a 3x3 map`() {
        val state = aGameState(map = aGameMap(cols = 3, rows = 3)).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state)
        val buffer = render(view, 30, 16)

        // Hex at (0,0): '/' at charX=0, charY=2
        assertEquals("/", buffer.get(0, 2).char)
        // Hex at (1,0): '/' at charX=7+1, charY=2+1
        assertEquals("/", buffer.get(8, 3).char)
        // Hex at (2,0): '/' at charX=14, charY=2
        assertEquals("/", buffer.get(14, 2).char)
    }

    @Test
    fun `renders unit id on hex`() {
        val unit = aUnit(id = "A1", name = "Atlas", position = HexCoordinates(0, 0))
        val state = aGameState(units = listOf(unit), map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state)
        val buffer = render(view, 30, 16)

        // Unit id "A1" at hex center: charX=4, charY=3
        assertEquals("A", buffer.get(4, 3).char)
        assertEquals("1", buffer.get(5, 3).char)
    }

    @Test
    fun `marks the cursor hex as focus, for the enclosing ScrollableView to auto-follow`() {
        val state = aGameState(map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val cursor = HexCoordinates(1, 1)
        val view = BoardView(state, cursorPosition = cursor)
        val canvas = Canvas.of(ScreenBuffer(30, 16))

        view.render(canvas)

        val (x, y) = HexLayout.hexToScreen(cursor.col, cursor.row)
        assertEquals(FocusRect(x, y, HexGeometry.HEX_WIDTH, HexGeometry.HEX_HEIGHT), canvas.focusRect())
    }

    @Test
    fun `no cursor means no focus is marked`() {
        val state = aGameState(map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state, cursorPosition = null)
        val canvas = Canvas.of(ScreenBuffer(30, 16))

        view.render(canvas)

        assertEquals(null, canvas.focusRect())
    }

    @Test
    fun `contentSize spans every hex in the map, including the odd-column row offset`() {
        val map = aGameMap(cols = 3, rows = 3)

        val (width, height) = BoardView.contentSize(map)

        // maxCol = 2, maxRow = 2
        assertEquals(2 * HexGeometry.COL_STRIDE + HexGeometry.HEX_WIDTH, width)
        assertEquals(2 * HexGeometry.ROW_STRIDE + HexGeometry.ODD_COL_ROW_OFFSET + HexGeometry.HEX_HEIGHT, height)
    }

    @Test
    fun `cursor position highlights hex`() {
        val state = aGameState(map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val cursor = HexCoordinates(1, 1)
        val view = BoardView(state, cursorPosition = cursor)
        val buffer = render(view, 30, 16)

        // Hex at (1,1) border '/' offset by +1,+1 within the hex glyph (row1)
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(8, 7).style.fg)
    }

    @Test
    fun `renders destroyed unit with its id and a skull marker`() {
        val unit = aUnit(id = "A1", name = "Atlas", position = HexCoordinates(0, 0)).copy(isDestroyed = true)
        val state = aGameState(units = listOf(unit), map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state)
        val buffer = render(view, 30, 16)

        // Unit id "A1" at hex center: charX=4/5, charY=3 (same cells as the id in the
        // "renders unit id on hex" test above), with a skull marker left of the id at charX=3.
        assertEquals("A", buffer.get(4, 3).char)
        assertEquals("1", buffer.get(5, 3).char)
        assertEquals(Color.GRAY, buffer.get(5, 3).style.fg)
        assertEquals(destroyedIcon(), buffer.get(3, 3).char)
        assertEquals(Color.GRAY, buffer.get(3, 3).style.fg)
    }

    @Test
    fun `prone unit still renders its lowercase id, distinct from destroyed`() {
        val unit = aUnit(id = "A1", name = "Atlas", position = HexCoordinates(0, 0)).copy(isProne = true)
        val state = aGameState(units = listOf(unit), map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val view = BoardView(state)
        val buffer = render(view, 30, 16)

        assertEquals("a", buffer.get(4, 3).char)
        assertEquals("1", buffer.get(5, 3).char)
    }

    @Test
    fun `highlights map with reachable and path overlays`() {
        val state = aGameState(map = aGameMap()).projectFor(viewer = null, revealAll = true)
        val highlights = mapOf(
            HexCoordinates(1, 0) to HexHighlight.REACHABLE_WALK,
            HexCoordinates(2, 0) to HexHighlight.PATH,
        )
        val view = BoardView(state, hexHighlights = highlights)
        val buffer = render(view, 30, 16)

        // Hex center is at x+4, y+2 from hex render origin
        assertEquals(".", buffer.get(11, 4).char)
        assertEquals("*", buffer.get(18, 2).char)
    }
}
