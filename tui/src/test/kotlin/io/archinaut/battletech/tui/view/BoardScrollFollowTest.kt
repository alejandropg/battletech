package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.query.PlayerGameState
import io.archinaut.battletech.tactical.query.projectFor
import io.archinaut.battletech.tui.aGameMap
import io.archinaut.battletech.tui.aGameState
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Padding
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.ScrollState
import io.archinaut.tenter.widget.Viewport
import io.archinaut.tenter.widget.ViewportState
import io.archinaut.tenter.widget.block
import io.archinaut.tenter.widget.fixedSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        // The board inside the same chrome a panel gives it: a titled border with horizontal
        // padding, and a top spacer row that scrolls away with the content.
        val board = BoardView(gameState, cursorPosition = cursor).fixedSize(width, height)
        val viewport = Viewport(
            board.block(Block(padding = Padding(top = 1))),
            block = Block.bordered(title = "TACTICAL MAP", padding = Padding.horizontal(1)),
        )
        viewport.render(Canvas.offscreen(80, 24), state)
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
