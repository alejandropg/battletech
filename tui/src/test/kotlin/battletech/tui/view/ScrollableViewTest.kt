package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Color
import battletech.tui.screen.FocusRect
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ScrollableViewTest {

    private fun stubContent(lines: Int): View = object : View {
        override fun render(canvas: Canvas) {
            for (i in 0 until lines) {
                canvas.writeString(0, i, "line$i")
            }
        }
    }

    /** [lines] rows of content; row [focusRow] marks itself as focus (for auto-follow tests). */
    private fun focusedContent(lines: Int, focusRow: Int): View = object : View {
        override fun render(canvas: Canvas) {
            for (i in 0 until lines) {
                canvas.writeString(0, i, "line$i")
                if (i == focusRow) canvas.markFocus(0, i, canvas.width, 1)
            }
        }
    }

    private fun panel(
        content: View,
        key: Char = '0',
        title: String = "T",
        scrollOffset: Int? = 0,
        previousFocus: FocusRect? = null,
        recenter: Boolean = false,
    ) = ScrollableView(
        title = title,
        badge = key.toString(),
        content = content,
        extent = ContentExtent.Measured(),
        offset = scrollOffset?.let { ScrollOffset(0, it) } ?: ScrollOffset.ZERO,
        previousFocus = previousFocus,
        recenter = recenter,
    )

    /** The focus rect [focusedContent] produces for [focusRow] in a [width]-wide panel stream. */
    private fun focusAt(focusRow: Int, width: Int = 26) = FocusRect(0, focusRow + 1, width, 1)

    // ── panel-chrome pixel parity (migrated from the pre-extraction ScrollablePanelView) ──────

    @Test
    fun `draws box and title at given coordinates`() {
        val view = panel(stubContent(0), key = '2', title = "FOO")

        val buffer = render(view, 30, 10)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(29, 0).char)
        assertEquals("[", buffer.get(2, 0).char)
        assertEquals("2", buffer.get(3, 0).char)
        assertEquals("]", buffer.get(4, 0).char)
        assertEquals("F", buffer.get(6, 0).char)
        assertEquals("O", buffer.get(7, 0).char)
        assertEquals("O", buffer.get(8, 0).char)
    }

    @Test
    fun `content at offset 0 starts at row y+2 col x+2`() {
        val view = panel(stubContent(5))

        val buffer = render(view, 30, 10)

        assertEquals("line0", buffer.line(2, 2, 10))
        assertEquals("line1", buffer.line(3, 2, 10))
    }

    @Test
    fun `explicit offset shifts the visible window`() {
        val view = panel(stubContent(20), scrollOffset = 3)

        val buffer = render(view, 30, 10)

        // Row 1 is the viewport's first row now that the padding scrolls with the stream.
        assertEquals("line2", buffer.line(1, 2, 10))
        assertEquals("line3", buffer.line(2, 2, 10))
        assertEquals("line4", buffer.line(3, 2, 10))
    }

    @Test
    fun `top padding shows at rest and is reclaimed once content scrolls`() {
        val atRest = render(panel(stubContent(20), scrollOffset = 0), 30, 10)
        assertEquals("", atRest.line(1, 2, 10))
        assertEquals("line0", atRest.line(2, 2, 10))
        assertEquals("line6", atRest.line(8, 2, 10))

        val scrolled = render(panel(stubContent(20), scrollOffset = 1), 30, 10)
        // Content reclaims the padding row: line0 moves up into row 1...
        assertEquals("line0", scrolled.line(1, 2, 10))
        // ...and one more line becomes visible at the bottom than was shown at rest.
        assertEquals("line7", scrolled.line(8, 2, 10))
    }

    @Test
    fun `offset beyond maxOffset is clamped to maxOffset`() {
        val view = panel(stubContent(5), scrollOffset = 999)

        val buffer = render(view, 30, 10)

        val viewportHeight = 8
        val streamHeight = 5 + 1 // the reclaimable top-padding row is prepended to the stream
        val maxOffset = maxOf(0, streamHeight - viewportHeight)
        assertEquals("line${maxOffset}", buffer.line(2, 2, 10))
    }

    @Test
    fun `maxOffset property equals contentHeight minus viewportHeight clamped to zero`() {
        val view = panel(stubContent(20))

        render(view, 30, 10)

        val viewportHeight = 8
        val streamHeight = 20 + 1 // the reclaimable top-padding row is prepended to the stream
        assertEquals(streamHeight - viewportHeight, view.state.maxOffset.y)
    }

    @Test
    fun `maxOffset is zero when content fits in viewport`() {
        val view = panel(stubContent(3))

        render(view, 30, 10)

        assertEquals(0, view.state.maxOffset.y)
    }

    @Test
    fun `null scrollOffset with no marked focus shows top of content`() {
        val view = panel(stubContent(20), scrollOffset = null)

        val buffer = render(view, 30, 10)

        assertEquals("line0", buffer.line(2, 2, 10))
    }

    @Test
    fun `scrollbar block cells appear on right border only when content overflows`() {
        val view = panel(stubContent(20))

        val buffer = render(view, 30, 10)

        // The viewport now spans the whole inner height (rows 1..8) — the padding row is part
        // of the scrollable stream, not a fixed offset — so the thumb can occupy row 1 too.
        val thumbRange = Scrollbar.thumb(track = 8, contentHeight = 21, viewportHeight = 8, offset = 0)!!
        for (row in 1..8) {
            val cell = buffer.get(29, row)
            if ((row - 1) in thumbRange) {
                assertEquals("▐", cell.char, "expected thumb at row $row")
                assertEquals(Color.GREEN, cell.style.fg)
            } else {
                assertEquals("│", cell.char, "expected border at row $row")
            }
        }
    }

    @Test
    fun `no scrollbar cells when content exactly fits viewport`() {
        val viewportHeight = 8
        // The stream includes one extra row for the reclaimable top padding, so
        // `viewportHeight - 1` content lines exactly fill it with no overflow.
        val view = panel(stubContent(viewportHeight - 1))

        val buffer = render(view, 30, 10)

        for (row in 1..8) {
            assertEquals("│", buffer.get(29, row).char, "expected plain border at row $row")
        }
    }

    @Test
    fun `degenerate height 2 draws only box and never throws`() {
        val view = panel(stubContent(5))

        val buffer = render(view, 30, 2)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╰", buffer.get(0, 1).char)
        assertEquals(0, view.state.maxOffset.y)
    }

    @Test
    fun `degenerate width 4 draws only box and never throws`() {
        val view = panel(stubContent(5))

        val buffer = render(view, 4, 10)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals(0, view.state.maxOffset.y)
    }

    @Test
    fun `content rendered at offset position within parent buffer`() {
        val view = panel(stubContent(5))
        val buffer = ScreenBuffer(40, 20)

        view.render(Canvas.of(buffer).region(5, 3, 20, 10))

        assertEquals("╭", buffer.get(5, 3).char)
        assertEquals("line0", buffer.line(5, 7, 10))
    }

    // ── ContentExtent.Fixed: two-axis, no measuring ────────────────────────────────────────────

    @Test
    fun `Fixed extent uses the given size without scanning content`() {
        val content = object : View {
            override fun render(canvas: Canvas) {
                canvas.writeString(0, 0, "X")
            }
        }
        val view = ScrollableView(
            title = "MAP", badge = null, content = content,
            extent = ContentExtent.Fixed(width = 50, height = 40),
            offset = ScrollOffset.ZERO,
        )

        render(view, 30, 10)

        // viewport ~= (30-4)x(10-2)=26x8; maxOffsetX = 50-26=24, maxOffsetY = (40+1)-8=33
        assertEquals(24, view.state.maxOffset.x)
        assertEquals(33, view.state.maxOffset.y)
    }

    @Test
    fun `horizontal scrolling is inert for Measured content — it is always exactly viewport-wide`() {
        val view = panel(stubContent(3))

        render(view, 30, 10)

        assertEquals(0, view.state.maxOffset.x)
        assertEquals(0, view.state.offset.x)
    }

    // ── auto-follow: fires on focus MOVEMENT, not on every render ──────────────────────────────

    @Test
    fun `unmarked content never auto-scrolls — offset is just the given base, clamped`() {
        val view = panel(stubContent(20), scrollOffset = 3)

        render(view, 30, 10)

        assertEquals(3, view.state.offset.y)
    }

    @Test
    fun `first render (no previous focus) follows focus below the window`() {
        // 20 lines, focus at line 15, starting scrolled to the top (focus well below the viewport).
        val view = panel(focusedContent(lines = 20, focusRow = 15), scrollOffset = 0, previousFocus = null)

        render(view, 30, 10)

        // viewport height = 8; focus (stream row 16, after the +1 padding shift) must be visible:
        // offset + 8 > 16 => offset >= 9; minimal shift => offset lands focus at the bottom edge.
        val offset = view.state.offset.y
        val streamFocusRow = 15 + 1
        assertEquals(true, streamFocusRow in offset until (offset + 8), "focus row not visible at offset $offset")
    }

    @Test
    fun `focus already visible leaves offset unchanged`() {
        val view = panel(focusedContent(lines = 20, focusRow = 0), scrollOffset = 0)

        render(view, 30, 10)

        assertEquals(0, view.state.offset.y)
    }

    /**
     * The regression this whole mechanism exists for: content marks its focus on *every* render,
     * so following unconditionally would drag the viewport back to the focus immediately after a
     * manual pan/wheel-scroll — undoing it on the very next event.
     */
    @Test
    fun `unmoved focus does NOT pull the viewport back — a manual scroll survives`() {
        val focusRow = 15
        val view = panel(
            focusedContent(lines = 40, focusRow = focusRow),
            scrollOffset = 3, // user scrolled here manually; focus is far below the window
            previousFocus = focusAt(focusRow),
        )

        render(view, 30, 10)

        assertEquals(3, view.state.offset.y)
    }

    @Test
    fun `moved focus follows, starting from the manually scrolled offset`() {
        val view = panel(
            focusedContent(lines = 40, focusRow = 20),
            scrollOffset = 3,
            previousFocus = focusAt(19), // focus was one row up last render — it moved
        )

        render(view, 30, 10)

        val offset = view.state.offset.y
        val streamFocusRow = 20 + 1
        assertEquals(true, streamFocusRow in offset until (offset + 8), "focus row not visible at offset $offset")
    }

    @Test
    fun `state reports the focus rect so callers can carry it to the next render`() {
        val view = panel(focusedContent(lines = 20, focusRow = 5), scrollOffset = 0)

        render(view, 30, 10)

        assertEquals(focusAt(5), view.state.focus)
    }

    @Test
    fun `recenter centers focus even when it has not moved`() {
        val focusRow = 20
        val view = panel(
            focusedContent(lines = 40, focusRow = focusRow),
            scrollOffset = 0,
            previousFocus = focusAt(focusRow),
            recenter = true,
        )

        render(view, 30, 10)

        // Centering a 1-row-tall focus: offset = focusStart - (viewportSize - focusSize) / 2.
        val viewportHeight = 8
        val streamFocusRow = focusRow + 1
        val expected = (streamFocusRow - (viewportHeight - 1) / 2).coerceIn(0, view.state.maxOffset.y)
        assertEquals(expected, view.state.offset.y)
    }

    @Test
    fun `degenerate viewport leaves state at ScrollState_NONE`() {
        val view = panel(stubContent(0), key = '0')

        render(view, 30, 2) // border alone consumes the whole height; render bails early

        assertEquals(ScrollState.NONE, view.state)
    }
}
