package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.PlayerGameState
import battletech.tactical.query.projectFor
import battletech.tui.aGameMap
import battletech.tui.aGameState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.view.ScrollState
import io.archinaut.tenter.view.ViewportState
import io.archinaut.tenter.view.fixedContent
import io.archinaut.tenter.view.render
import io.archinaut.tenter.view.scrollingPanel

internal class BoardScrollFollowTest {

    private val gameState: PlayerGameState =
        aGameState(map = aGameMap(cols = 30, rows = 20)).projectFor(viewer = null, revealAll = true)

    private fun renderPass(
        cursor: HexCoordinates,
        state: ViewportState,
        recenter: Boolean = false,
    ): ScrollState {
        if (recenter) state.requestRecenter()
        val (width, height) = BoardView.contentSize(gameState.map)
        val view = scrollingPanel(
            title = "TACTICAL MAP",
            badge = null,
            content = fixedContent(width, height, BoardView(gameState, cursorPosition = cursor)),
            state = state,
        )
        render(view, 80, 24)
        return checkNotNull(state.settled) { "no drawable frame" }
    }

    @Test
    fun `a pan survives every subsequent render while the cursor stays put`() {
        val cursor = HexCoordinates(0, 0)
        val state = ViewportState()

        val first = renderPass(cursor, state)
        assertTrue(first.maxOffset.x > 0)
        state.scrollBy(21, 0)
        val second = renderPass(cursor, state)
        assertEquals(21, second.offset.x)
        val third = renderPass(cursor, state)
        assertEquals(21, third.offset.x)
        val fourth = renderPass(cursor, state)
        assertEquals(21, fourth.offset.x)
    }

    @Test
    fun `moving the cursor re-engages follow from wherever the user panned to`() {
        val state = ViewportState()
        val first = renderPass(HexCoordinates(0, 0), state)
        state.scrollBy(70, 0)
        val second = renderPass(HexCoordinates(0, 0), state)
        assertEquals(70, second.offset.x)
        val third = renderPass(HexCoordinates(1, 1), state)

        assertNotEquals(70, third.offset.x)
        val revealed = third.revealed!!
        assertTrue(revealed.x >= third.offset.x && revealed.x + revealed.width <= third.offset.x + 76)
        assertTrue(first.maxOffset.x > 0)
    }

    @Test
    fun `recenter pulls the board back to the cursor even though the cursor never moved`() {
        val cursor = HexCoordinates(10, 8)
        val state = ViewportState()
        val first = renderPass(cursor, state)
        state.scrollBy(50, 0)
        val second = renderPass(cursor, state)
        assertEquals(first.offset.x + 50, second.offset.x)

        val third = renderPass(cursor, state, recenter = true)
        val revealed = third.revealed!!
        assertTrue(revealed.x >= third.offset.x && revealed.x + revealed.width <= third.offset.x + 76)
        assertNotEquals(second.offset.x, third.offset.x)
    }
}
