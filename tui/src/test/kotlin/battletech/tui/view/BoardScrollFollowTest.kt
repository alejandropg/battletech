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
import tenter.screen.RevealRect
import tenter.view.ContentExtent
import tenter.view.ScrollOffset
import tenter.view.ScrollState
import tenter.view.ScrollingPanel
import tenter.view.render
import tenter.view.scrollingPanel

/**
 * The board composed the way [Workspace.render] composes it — real [BoardView] content inside a
 * [scrollingPanel] — driven across consecutive renders with the reveal target carried forward,
 * which is the only place the pan-then-snap-back defect was observable.
 */
internal class BoardScrollFollowTest {

    private val state: PlayerGameState =
        aGameState(map = aGameMap(cols = 30, rows = 20)).projectFor(viewer = null, revealAll = true)

    private fun board(
        cursor: HexCoordinates,
        offset: ScrollOffset,
        previousReveal: RevealRect?,
        recenter: Boolean = false,
    ): ScrollingPanel {
        val (w, h) = BoardView.contentSize(state.map)
        return scrollingPanel(
            title = "TACTICAL MAP",
            badge = null,
            content = BoardView(state, cursorPosition = cursor),
            extent = ContentExtent.Fixed(w, h),
            offset = offset,
            previousReveal = previousReveal,
            recenter = recenter,
        )
    }

    /** One render pass, returning what the board settled on — mirrors the loop's frame bookkeeping. */
    private fun renderPass(
        cursor: HexCoordinates,
        offset: ScrollOffset,
        previousReveal: RevealRect?,
        recenter: Boolean = false,
    ): ScrollState {
        val view = board(cursor, offset, previousReveal, recenter)
        render(view, 80, 24)
        return view.scroll
    }

    @Test
    fun `a pan survives every subsequent render while the cursor stays put`() {
        val cursor = HexCoordinates(0, 0)

        // Frame 1: initial render, cursor at origin, no previous reveal — follows into view.
        val first = renderPass(cursor, ScrollOffset.ZERO, previousReveal = null)
        assertTrue(first.maxOffset.x > 0, "map must be wider than the viewport for this test to mean anything")

        // Frame 2: the user pans right. The loop adds the delta and re-renders.
        val panned = ScrollOffset(first.offset.x + 21, first.offset.y)
        val second = renderPass(cursor, panned, previousReveal = first.revealed)
        assertEquals(21, second.offset.x, "the pan itself must take effect")

        // Frames 3 and 4: unrelated events (a panel toggle, a session event — anything that
        // re-renders). The cursor has not moved, so the board must NOT crawl back to it.
        val third = renderPass(cursor, second.offset, previousReveal = second.revealed)
        assertEquals(21, third.offset.x, "an unrelated re-render must not undo the pan")

        val fourth = renderPass(cursor, third.offset, previousReveal = third.revealed)
        assertEquals(21, fourth.offset.x, "the pan must stay put indefinitely")
    }

    @Test
    fun `moving the cursor re-engages follow from wherever the user panned to`() {
        val start = HexCoordinates(0, 0)
        val first = renderPass(start, ScrollOffset.ZERO, previousReveal = null)

        // Pan far away from the cursor.
        val panned = ScrollOffset(first.offset.x + 70, first.offset.y)
        val second = renderPass(start, panned, previousReveal = first.revealed)
        assertEquals(70, second.offset.x)

        // Now the cursor moves: the reveal target changed, so the board follows it back into view.
        val moved = HexCoordinates(1, 1)
        val third = renderPass(moved, second.offset, previousReveal = second.revealed)

        assertNotEquals(70, third.offset.x, "cursor movement must re-engage follow")
        val revealed = third.revealed!!
        assertTrue(
            revealed.x >= third.offset.x && revealed.x + revealed.width <= third.offset.x + 76,
            "cursor hex must be inside the viewport after following (offset=${third.offset.x}, revealed=$revealed)",
        )
    }

    @Test
    fun `recenter pulls the board back to the cursor even though the cursor never moved`() {
        val cursor = HexCoordinates(10, 8)
        val first = renderPass(cursor, ScrollOffset.ZERO, previousReveal = null)

        val panned = ScrollOffset(first.offset.x + 50, first.offset.y)
        val second = renderPass(cursor, panned, previousReveal = first.revealed)
        assertEquals(first.offset.x + 50, second.offset.x)

        // Home: recenter wins despite the reveal target being unchanged.
        val third = renderPass(cursor, second.offset, previousReveal = second.revealed, recenter = true)

        val revealed = third.revealed!!
        assertTrue(
            revealed.x >= third.offset.x && revealed.x + revealed.width <= third.offset.x + 76,
            "cursor hex must be visible after recenter (offset=${third.offset.x}, revealed=$revealed)",
        )
        assertNotEquals(second.offset.x, third.offset.x, "recenter must actually move the viewport")
    }
}
