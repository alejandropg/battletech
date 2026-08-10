package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.FocusRect
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [Scrolled]'s own contract — extents, offset clamping, and auto-follow — exercised directly
 * against a bare viewport with no border, padding, or scrollbar involved. See [BorderedTest] for
 * the box/badge/thumb decoration and `ScrollingPanelTest` for the two composed together, which is
 * how every side panel and the tactical board actually render.
 */
internal class ScrolledTest {

    private val viewportWidth = 26
    private val viewportHeight = 8

    private fun stubContent(lines: Int): View = object : View {
        override fun render(canvas: Canvas) {
            for (i in 0 until lines) canvas.writeString(0, i, "line$i")
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

    /** The focus rect [focusedContent] produces for [focusRow] in a [viewportWidth]-wide stream. */
    private fun focusAt(focusRow: Int, width: Int = viewportWidth) = FocusRect(0, focusRow, width, 1)

    private fun renderScrolled(
        content: View,
        extent: ContentExtent = ContentExtent.Measured(),
        offset: ScrollOffset = ScrollOffset.ZERO,
        previousFocus: FocusRect? = null,
        recenter: Boolean = false,
        width: Int = viewportWidth,
        height: Int = viewportHeight,
    ): Pair<Scrolled, ScreenBuffer> {
        val scrolled = Scrolled(content, extent, offset, previousFocus, recenter)
        val buffer = ScreenBuffer(width, height)
        scrolled.render(Canvas.of(buffer))
        return scrolled to buffer
    }

    @Test
    fun `content renders flush at the viewport's own 0,0`() {
        val (_, buffer) = renderScrolled(stubContent(5))

        assertEquals("line0", buffer.line(0, 0, 10))
        assertEquals("line1", buffer.line(1, 0, 10))
    }

    @Test
    fun `explicit offset shifts the visible window`() {
        val (_, buffer) = renderScrolled(stubContent(20), offset = ScrollOffset(0, 3))

        assertEquals("line3", buffer.line(0, 0, 10))
        assertEquals("line4", buffer.line(1, 0, 10))
    }

    @Test
    fun `offset beyond maxOffset is clamped to maxOffset`() {
        val (scrolled, buffer) = renderScrolled(stubContent(20), offset = ScrollOffset(0, 999))

        assertEquals(12, scrolled.scroll.maxOffset.y) // 20 lines - 8 viewport rows
        assertEquals("line12", buffer.line(0, 0, 10))
    }

    @Test
    fun `maxOffset equals contentHeight minus viewportHeight`() {
        val (scrolled, _) = renderScrolled(stubContent(20))

        assertEquals(12, scrolled.scroll.maxOffset.y)
    }

    @Test
    fun `maxOffset is zero when content fits in viewport`() {
        val (scrolled, _) = renderScrolled(stubContent(3))

        assertEquals(0, scrolled.scroll.maxOffset.y)
    }

    // ── ContentExtent.Fixed: two-axis, no measuring ────────────────────────────────────────────

    @Test
    fun `Fixed extent uses the given size without scanning content`() {
        val content = object : View {
            override fun render(canvas: Canvas) {
                canvas.writeString(0, 0, "X")
            }
        }
        val (scrolled, _) = renderScrolled(content, extent = ContentExtent.Fixed(width = 50, height = 40))

        assertEquals(24, scrolled.scroll.maxOffset.x) // 50 - 26
        assertEquals(32, scrolled.scroll.maxOffset.y) // 40 - 8
    }

    @Test
    fun `horizontal scrolling is inert for Measured content — it is always exactly viewport-wide`() {
        val (scrolled, _) = renderScrolled(stubContent(3))

        assertEquals(0, scrolled.scroll.maxOffset.x)
        assertEquals(0, scrolled.scroll.offset.x)
    }

    // ── auto-follow: fires on focus MOVEMENT, not on every render ──────────────────────────────

    @Test
    fun `unmarked content never auto-scrolls — offset is just the given base, clamped`() {
        val (scrolled, _) = renderScrolled(stubContent(20), offset = ScrollOffset(0, 3))

        assertEquals(3, scrolled.scroll.offset.y)
    }

    @Test
    fun `first render (no previous focus) follows focus below the window`() {
        val (scrolled, _) = renderScrolled(focusedContent(lines = 20, focusRow = 15), previousFocus = null)

        val offset = scrolled.scroll.offset.y
        assertEquals(true, 15 in offset until (offset + viewportHeight), "focus row not visible at offset $offset")
    }

    @Test
    fun `focus already visible leaves offset unchanged`() {
        val (scrolled, _) = renderScrolled(focusedContent(lines = 20, focusRow = 0))

        assertEquals(0, scrolled.scroll.offset.y)
    }

    /**
     * The regression this whole mechanism exists for: content marks its focus on *every* render,
     * so following unconditionally would drag the viewport back to the focus immediately after a
     * manual pan/wheel-scroll — undoing it on the very next event.
     */
    @Test
    fun `unmoved focus does NOT pull the viewport back — a manual scroll survives`() {
        val focusRow = 15
        val (scrolled, _) = renderScrolled(
            focusedContent(lines = 40, focusRow = focusRow),
            offset = ScrollOffset(0, 3), // user scrolled here manually; focus is far below the window
            previousFocus = focusAt(focusRow),
        )

        assertEquals(3, scrolled.scroll.offset.y)
    }

    @Test
    fun `moved focus follows, starting from the manually scrolled offset`() {
        val (scrolled, _) = renderScrolled(
            focusedContent(lines = 40, focusRow = 20),
            offset = ScrollOffset(0, 3),
            previousFocus = focusAt(19), // focus was one row up last render — it moved
        )

        val offset = scrolled.scroll.offset.y
        assertEquals(true, 20 in offset until (offset + viewportHeight), "focus row not visible at offset $offset")
    }

    @Test
    fun `state reports the focus rect so callers can carry it to the next render`() {
        val (scrolled, _) = renderScrolled(focusedContent(lines = 20, focusRow = 5))

        assertEquals(focusAt(5), scrolled.scroll.focus)
    }

    @Test
    fun `recenter centers focus even when it has not moved`() {
        val focusRow = 20
        val (scrolled, _) = renderScrolled(
            focusedContent(lines = 40, focusRow = focusRow),
            previousFocus = focusAt(focusRow),
            recenter = true,
        )

        // Centering a 1-row-tall focus: offset = focusStart - (viewportSize - focusSize) / 2.
        val expected = (focusRow - (viewportHeight - 1) / 2).coerceIn(0, scrolled.scroll.maxOffset.y)
        assertEquals(expected, scrolled.scroll.offset.y)
    }

    @Test
    fun `degenerate viewport leaves state at ScrollState_NONE`() {
        val scrolled = Scrolled(stubContent(0), ContentExtent.Measured())

        scrolled.render(Canvas.offscreen(30, 0)) // no rows to render into; bails early

        assertEquals(ScrollState.NONE, scrolled.scroll)
    }
}
