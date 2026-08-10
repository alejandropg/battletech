package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.PlayerGameState
import battletech.tactical.query.projectFor
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.screen.FocusRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The board composed the way `RunLoop.renderFrame` composes it — real [BoardView] content inside a
 * [ScrollableView] — driven across consecutive renders with focus carried forward, which is the
 * only place the pan-then-snap-back defect was observable.
 */
internal class BoardScrollFollowTest {

    private val state: PlayerGameState =
        aGameState(map = aGameMap(cols = 30, rows = 20)).projectFor(viewer = null, revealAll = true)

    private fun board(
        cursor: HexCoordinates,
        offset: ScrollOffset,
        previousFocus: FocusRect?,
        recenter: Boolean = false,
    ): ScrollableView {
        val (w, h) = BoardView.contentSize(state.map)
        return ScrollableView(
            title = "TACTICAL MAP",
            badge = null,
            content = BoardView(state, cursorPosition = cursor),
            extent = ContentExtent.Fixed(w, h),
            offset = offset,
            previousFocus = previousFocus,
            recenter = recenter,
        )
    }

    /** One render pass, returning what the board settled on — mirrors the loop's frame bookkeeping. */
    private fun renderPass(
        cursor: HexCoordinates,
        offset: ScrollOffset,
        previousFocus: FocusRect?,
        recenter: Boolean = false,
    ): ScrollState {
        val view = board(cursor, offset, previousFocus, recenter)
        render(view, 80, 24)
        return view.scroll
    }

    @Test
    fun `a pan survives every subsequent render while the cursor stays put`() {
        val cursor = HexCoordinates(0, 0)

        // Frame 1: initial render, cursor at origin, no previous focus — follows into view.
        val first = renderPass(cursor, ScrollOffset.ZERO, previousFocus = null)
        assertTrue(first.maxOffset.x > 0, "map must be wider than the viewport for this test to mean anything")

        // Frame 2: the user pans right. The loop adds the delta and re-renders.
        val panned = ScrollOffset(first.offset.x + 21, first.offset.y)
        val second = renderPass(cursor, panned, previousFocus = first.focus)
        assertEquals(21, second.offset.x, "the pan itself must take effect")

        // Frames 3 and 4: unrelated events (a panel toggle, a session event — anything that
        // re-renders). The cursor has not moved, so the board must NOT crawl back to it.
        val third = renderPass(cursor, second.offset, previousFocus = second.focus)
        assertEquals(21, third.offset.x, "an unrelated re-render must not undo the pan")

        val fourth = renderPass(cursor, third.offset, previousFocus = third.focus)
        assertEquals(21, fourth.offset.x, "the pan must stay put indefinitely")
    }

    @Test
    fun `moving the cursor re-engages follow from wherever the user panned to`() {
        val start = HexCoordinates(0, 0)
        val first = renderPass(start, ScrollOffset.ZERO, previousFocus = null)

        // Pan far away from the cursor.
        val panned = ScrollOffset(first.offset.x + 70, first.offset.y)
        val second = renderPass(start, panned, previousFocus = first.focus)
        assertEquals(70, second.offset.x)

        // Now the cursor moves: focus changed, so the board follows it back into view.
        val moved = HexCoordinates(1, 1)
        val third = renderPass(moved, second.offset, previousFocus = second.focus)

        assertNotEquals(70, third.offset.x, "cursor movement must re-engage follow")
        val focus = third.focus!!
        assertTrue(
            focus.x >= third.offset.x && focus.x + focus.width <= third.offset.x + 76,
            "cursor hex must be inside the viewport after following (offset=${third.offset.x}, focus=$focus)",
        )
    }

    @Test
    fun `recenter pulls the board back to the cursor even though the cursor never moved`() {
        val cursor = HexCoordinates(10, 8)
        val first = renderPass(cursor, ScrollOffset.ZERO, previousFocus = null)

        val panned = ScrollOffset(first.offset.x + 50, first.offset.y)
        val second = renderPass(cursor, panned, previousFocus = first.focus)
        assertEquals(first.offset.x + 50, second.offset.x)

        // Home: recenter wins despite the focus being unchanged.
        val third = renderPass(cursor, second.offset, previousFocus = second.focus, recenter = true)

        val focus = third.focus!!
        assertTrue(
            focus.x >= third.offset.x && focus.x + focus.width <= third.offset.x + 76,
            "cursor hex must be visible after recenter (offset=${third.offset.x}, focus=$focus)",
        )
        assertNotEquals(second.offset.x, third.offset.x, "recenter must actually move the viewport")
    }
}
