package tenter.view

import tenter.screen.Canvas
import tenter.screen.RevealRect

/** A 2D scroll offset, in characters. */
public data class ScrollOffset(val x: Int = 0, val y: Int = 0) {
    public operator fun plus(other: ScrollOffset): ScrollOffset = ScrollOffset(x + other.x, y + other.y)

    public companion object {
        public val ZERO: ScrollOffset = ScrollOffset()
    }
}

/**
 * What a [Viewport] actually rendered at: the offset it settled on, each axis's max, and the
 * rect its content [revealed]. [revealed] must be carried back in as the next render's
 * `previousReveal` — that comparison is what distinguishes "the reveal target moved, chase it"
 * from "the user scrolled, leave it alone".
 */
public data class ScrollState(
    val offset: ScrollOffset,
    val maxOffset: ScrollOffset,
    val revealed: RevealRect? = null,
) {
    public companion object {
        public val NONE: ScrollState = ScrollState(ScrollOffset.ZERO, ScrollOffset.ZERO)
    }
}

/**
 * How much content a [Viewport] must accommodate, and how to find out.
 *
 * Which axes actually scroll is never chosen explicitly — it falls out of the extent: an axis's
 * max offset is `contentSize - viewportSize`, so [Measured] content (always exactly
 * viewport-wide) is inert horizontally with no one having to say so.
 */
public sealed interface ContentExtent {
    /** Content is exactly viewport-wide; height is discovered by rendering into up to [maxHeight] rows and measuring. */
    public data class Measured(val maxHeight: Int = 512) : ContentExtent

    /** Content's size is already known — no render-then-measure pass. */
    public data class Fixed(val width: Int, val height: Int) : ContentExtent
}

/**
 * The one place that knows how to scroll: an offscreen content stream, clamping, and blitting
 * into whatever canvas it is given — that canvas IS the viewport, at full size, with no border or
 * padding of its own. Chrome (border, title, padding, scrollbars) is a separate decorator; see
 * [scrollingPanel] for how a caller composes the two.
 *
 * Content opts into auto-follow with nothing more than [Canvas.markReveal] — see its KDoc. Content
 * that never marks a reveal scrolls manually only, exactly as before this class existed.
 *
 * ### Follow on change, not every render
 * Auto-follow fires when the reveal rect **differs from [previousReveal]** — not on every render.
 * That distinction is the whole behaviour: content marks its reveal every time it draws, so
 * following unconditionally would drag the viewport back to it immediately after any manual pan
 * or wheel-scroll, undoing it on the very next event. Reveal rects are in content-stream
 * coordinates, independent of scroll, so "reveal differs" means exactly "the thing worth watching
 * moved" and never "the user scrolled". Callers persist [ScrollState.revealed] between renders.
 */
public class Viewport(
    private val content: View,
    private val extent: ContentExtent,
    private val offset: ScrollOffset = ScrollOffset.ZERO,
    private val previousReveal: RevealRect? = null,
    private val recenter: Boolean = false,
) : View {

    /**
     * What was actually rendered — the effective offset and each axis's max. Set by [draw].
     * Consumers outside this file should generally prefer [ScrollingPanel.scroll], which reaches
     * this without callers needing to know a [Bordered] box wraps a [Viewport] internally.
     */
    public var scroll: ScrollState = ScrollState.NONE
        private set

    override fun draw(canvas: Canvas) {
        if (canvas.width <= 0 || canvas.height <= 0) {
            scroll = ScrollState.NONE
            return
        }

        val streamWidth = when (extent) {
            is ContentExtent.Measured -> canvas.width
            is ContentExtent.Fixed -> extent.width
        }
        val allocatedHeight = when (extent) {
            is ContentExtent.Measured -> extent.maxHeight
            is ContentExtent.Fixed -> extent.height
        }

        val stream = Canvas.offscreen(streamWidth, allocatedHeight)
        content.draw(stream)

        val streamHeight = when (extent) {
            is ContentExtent.Measured -> stream.contentHeight()
            is ContentExtent.Fixed -> allocatedHeight
        }

        val maxOffsetX = (streamWidth - canvas.width).coerceAtLeast(0)
        val maxOffsetY = (streamHeight - canvas.height).coerceAtLeast(0)

        val reveal = stream.revealRect()
        val baseX = offset.x
        val baseY = offset.y

        // Follow only when the reveal target moved (or a recenter was explicitly asked for);
        // otherwise the given offset stands, so a manual pan/scroll survives every subsequent render.
        val shouldFollow = reveal != null && reveal != previousReveal

        val offsetX = resolveAxis(baseX, reveal?.let { it.x to it.x + it.width }, canvas.width, maxOffsetX, shouldFollow)
        val offsetY = resolveAxis(baseY, reveal?.let { it.y to it.y + it.height }, canvas.height, maxOffsetY, shouldFollow)

        canvas.blit(stream, offsetX, offsetY, 0, 0, canvas.width, canvas.height)

        scroll = ScrollState(ScrollOffset(offsetX, offsetY), ScrollOffset(maxOffsetX, maxOffsetY), reveal)
    }

    private fun resolveAxis(
        base: Int,
        revealRange: Pair<Int, Int>?,
        viewportSize: Int,
        maxOffset: Int,
        shouldFollow: Boolean,
    ): Int = when {
        revealRange == null -> base.coerceIn(0, maxOffset)
        recenter -> ScrollGeometry.center(revealRange.first, revealRange.second, viewportSize, maxOffset)
        shouldFollow -> ScrollGeometry.follow(base, revealRange.first, revealRange.second, viewportSize, maxOffset)
        else -> base.coerceIn(0, maxOffset)
    }
}
