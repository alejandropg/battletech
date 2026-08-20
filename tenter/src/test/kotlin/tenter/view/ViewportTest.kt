package tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.RevealRect
import tenter.screen.ScreenBuffer

/**
 * [Viewport]'s own contract — extents, offset clamping, and auto-follow — exercised directly
 * against a bare viewport with no border, padding, or scrollbar involved. See [BorderedTest] for
 * the box/badge/thumb decoration and `ScrollingPanelTest` for the two composed together, which is
 * how a scrolling panel actually renders.
 */
internal class ViewportTest {

    private val viewportWidth = 26
    private val viewportHeight = 8

    private fun stubContent(lines: Int): View = object : View {
        override fun draw(canvas: Canvas) {
            for (i in 0 until lines) canvas.writeString(0, i, "line$i")
        }
    }

    /** [lines] rows of content; row [revealRow] marks itself for reveal (for auto-follow tests). */
    private fun revealingContent(lines: Int, revealRow: Int): View = object : View {
        override fun draw(canvas: Canvas) {
            for (i in 0 until lines) {
                canvas.writeString(0, i, "line$i")
                if (i == revealRow) canvas.markReveal(0, i, canvas.width, 1)
            }
        }
    }

    /** The reveal rect [revealingContent] produces for [revealRow] in a [viewportWidth]-wide stream. */
    private fun revealAt(revealRow: Int, width: Int = viewportWidth) = RevealRect(0, revealRow, width, 1)

    private fun renderScrolled(
        content: View,
        extent: ContentExtent = ContentExtent.Measured(),
        offset: ScrollOffset = ScrollOffset.ZERO,
        previousReveal: RevealRect? = null,
        recenter: Boolean = false,
        width: Int = viewportWidth,
        height: Int = viewportHeight,
    ): Pair<Viewport, ScreenBuffer> {
        val scrolled = Viewport(content, extent, offset, previousReveal, recenter)
        val buffer = ScreenBuffer(width, height)
        scrolled.draw(Canvas.of(buffer))
        return scrolled to buffer
    }

    @Test
    fun `content renders flush at the viewport's own 0,0`() {
        val (_, buffer) = renderScrolled(stubContent(5))

        assertEquals("line0", buffer.line(0, width = 10))
        assertEquals("line1", buffer.line(1, width = 10))
    }

    @Test
    fun `explicit offset shifts the visible window`() {
        val (_, buffer) = renderScrolled(stubContent(20), offset = ScrollOffset(y = 3))

        assertEquals("line3", buffer.line(0, width = 10))
        assertEquals("line4", buffer.line(1, width = 10))
    }

    @Test
    fun `offset beyond maxOffset is clamped to maxOffset`() {
        val (scrolled, buffer) = renderScrolled(stubContent(20), offset = ScrollOffset(y = 999))

        assertEquals(12, scrolled.scroll.maxOffset.y) // 20 lines - 8 viewport rows
        assertEquals("line12", buffer.line(0, width = 10))
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
            override fun draw(canvas: Canvas) {
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

    // ── auto-follow: fires on reveal-target MOVEMENT, not on every render ──────────────────────

    @Test
    fun `unmarked content never auto-scrolls — offset is just the given base, clamped`() {
        val (scrolled, _) = renderScrolled(stubContent(20), offset = ScrollOffset(y = 3))

        assertEquals(3, scrolled.scroll.offset.y)
    }

    @Test
    fun `first render (no previous reveal) follows reveal target below the window`() {
        val (scrolled, _) = renderScrolled(revealingContent(lines = 20, revealRow = 15))

        val offset = scrolled.scroll.offset.y
        assertEquals(true, 15 in offset until (offset + viewportHeight), "reveal row not visible at offset $offset")
    }

    @Test
    fun `reveal target already visible leaves offset unchanged`() {
        val (scrolled, _) = renderScrolled(revealingContent(lines = 20, revealRow = 0))

        assertEquals(0, scrolled.scroll.offset.y)
    }

    /**
     * The regression this whole mechanism exists for: content marks its reveal target on *every*
     * render, so following unconditionally would drag the viewport back to it immediately after a
     * manual pan/wheel-scroll — undoing it on the very next event.
     */
    @Test
    fun `unmoved reveal target does NOT pull the viewport back — a manual scroll survives`() {
        val revealRow = 15
        val (scrolled, _) = renderScrolled(
            revealingContent(lines = 40, revealRow = revealRow),
            offset = ScrollOffset(y = 3), // user scrolled here manually; reveal target is far below the window
            previousReveal = revealAt(revealRow),
        )

        assertEquals(3, scrolled.scroll.offset.y)
    }

    @Test
    fun `moved reveal target follows, starting from the manually scrolled offset`() {
        val (scrolled, _) = renderScrolled(
            revealingContent(lines = 40, revealRow = 20),
            offset = ScrollOffset(y = 3),
            previousReveal = revealAt(19), // reveal target was one row up last render — it moved
        )

        val offset = scrolled.scroll.offset.y
        assertEquals(true, 20 in offset until (offset + viewportHeight), "reveal row not visible at offset $offset")
    }

    @Test
    fun `state reports the reveal rect so callers can carry it to the next render`() {
        val (scrolled, _) = renderScrolled(revealingContent(lines = 20, revealRow = 5))

        assertEquals(revealAt(5), scrolled.scroll.revealed)
    }

    @Test
    fun `recenter centers the reveal target even when it has not moved`() {
        val revealRow = 20
        val (scrolled, _) = renderScrolled(
            revealingContent(lines = 40, revealRow = revealRow),
            previousReveal = revealAt(revealRow),
            recenter = true,
        )

        // Centering a 1-row-tall reveal target: offset = revealStart - (viewportSize - revealSize) / 2.
        val expected = (revealRow - (viewportHeight - 1) / 2).coerceIn(0, scrolled.scroll.maxOffset.y)
        assertEquals(expected, scrolled.scroll.offset.y)
    }

    @Test
    fun `degenerate viewport leaves state at ScrollState_NONE`() {
        val scrolled = Viewport(stubContent(0), ContentExtent.Measured())

        scrolled.draw(Canvas.offscreen(30, 0)) // no rows to render into; bails early

        assertEquals(ScrollState.NONE, scrolled.scroll)
    }
}
