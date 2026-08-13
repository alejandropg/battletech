package tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas

/**
 * [scrollingPanel] composes [Bordered], [Viewport], and [Padded] — border/badge in [BorderedTest],
 * scroll math in [ScrolledTest] — into what a scrolling panel actually renders. These cases
 * exercise the composition itself: specifically, that [Bordered.PADDING]'s top row is folded into
 * the *content stream* (via [Padded]) rather than the viewport, which is what makes it a spacer
 * at rest that the content reclaims the instant the view scrolls.
 */
internal class ScrollingPanelTest {

    private fun stubContent(lines: Int): View = object : View {
        override fun draw(canvas: Canvas) {
            for (i in 0 until lines) canvas.writeString(0, i, "line$i")
        }
    }

    private fun panel(content: View, scrollOffset: Int? = 0) = scrollingPanel(
        title = "T",
        badge = "0",
        content = content,
        extent = ContentExtent.Measured(),
        offset = scrollOffset?.let { ScrollOffset(0, it) } ?: ScrollOffset.ZERO,
    )

    @Test
    fun `content at offset 0 starts at row y+2 col x+2`() {
        val buffer = render(panel(stubContent(5)), 30, 10)

        assertEquals("line0", buffer.line(2, 2, 10))
        assertEquals("line1", buffer.line(3, 2, 10))
    }

    @Test
    fun `explicit offset shifts the visible window`() {
        val buffer = render(panel(stubContent(20), scrollOffset = 3), 30, 10)

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
    fun `maxOffset accounts for the reclaimable top-padding row`() {
        val view = panel(stubContent(20))

        render(view, 30, 10)

        val viewportHeight = 8
        val streamHeight = 20 + 1 // the reclaimable top-padding row is prepended to the stream
        assertEquals(streamHeight - viewportHeight, view.scroll.maxOffset.y)
    }

    @Test
    fun `degenerate height 2 draws only box and never throws`() {
        val view = panel(stubContent(5))

        val buffer = render(view, 30, 2)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╰", buffer.get(0, 1).char)
        assertEquals(0, view.scroll.maxOffset.y)
    }

    @Test
    fun `degenerate width 4 draws only box and never throws`() {
        val view = panel(stubContent(5))

        val buffer = render(view, 4, 10)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals(0, view.scroll.maxOffset.y)
    }
}
