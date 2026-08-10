package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.FocusRect

/** A 2D scroll offset, in characters. */
internal data class ScrollOffset(val x: Int = 0, val y: Int = 0) {
    operator fun plus(other: ScrollOffset): ScrollOffset = ScrollOffset(x + other.x, y + other.y)

    companion object {
        val ZERO: ScrollOffset = ScrollOffset(0, 0)
    }
}

/**
 * What a [Scrolled] actually rendered at: the offset it settled on, each axis's max, and the
 * [focus] its content marked. [focus] must be carried back in as the next render's
 * `previousFocus` — that comparison is what distinguishes "the focus moved, chase it" from "the
 * user scrolled, leave it alone".
 */
internal data class ScrollState(
    val offset: ScrollOffset,
    val maxOffset: ScrollOffset,
    val focus: FocusRect? = null,
) {
    companion object {
        val NONE: ScrollState = ScrollState(ScrollOffset.ZERO, ScrollOffset.ZERO)
    }
}

/**
 * How much content a [Scrolled] must accommodate, and how to find out.
 *
 * Which axes actually scroll is never chosen explicitly — it falls out of the extent: an axis's
 * max offset is `contentSize - viewportSize`, so [Measured] content (always exactly
 * viewport-wide) is inert horizontally with no one having to say so.
 */
internal sealed interface ContentExtent {
    /** Content is exactly viewport-wide; height is discovered by rendering into up to [maxHeight] rows and measuring. */
    data class Measured(val maxHeight: Int = 512) : ContentExtent

    /** Content's size is already known — no render-then-measure pass. */
    data class Fixed(val width: Int, val height: Int) : ContentExtent
}

/**
 * The one place in the TUI that knows how to scroll: an offscreen content stream, clamping, and
 * blitting into whatever canvas it is given — that canvas IS the viewport, at full size, with no
 * border or padding of its own. Chrome (border, title, padding, scrollbars) is a separate
 * decorator; see [scrollingPanel] for how side panels and the tactical board compose the two.
 *
 * Content opts into auto-follow with nothing more than [Canvas.markFocus] — see its KDoc. Content
 * that never marks focus scrolls manually only, exactly as before this class existed.
 *
 * ### Follow on change, not every render
 * Auto-follow fires when the focus rect **differs from [previousFocus]** — not on every render.
 * That distinction is the whole behaviour: content marks its focus every time it draws, so
 * following unconditionally would drag the viewport back to the focus immediately after any
 * manual pan or wheel-scroll, undoing it on the very next event. Focus rects are in content-stream
 * coordinates, independent of scroll, so "focus differs" means exactly "the thing worth watching
 * moved" and never "the user scrolled". Callers persist [ScrollState.focus] between renders.
 */
internal class Scrolled(
    private val content: View,
    private val extent: ContentExtent,
    private val offset: ScrollOffset = ScrollOffset.ZERO,
    private val previousFocus: FocusRect? = null,
    private val recenter: Boolean = false,
) : View {

    /** What was actually rendered — the effective offset and each axis's max. Set by [render]. */
    var scroll: ScrollState = ScrollState.NONE
        private set

    override fun render(canvas: Canvas) {
        val viewport = canvas
        if (viewport.width <= 0 || viewport.height <= 0) {
            scroll = ScrollState.NONE
            return
        }

        val streamWidth = when (extent) {
            is ContentExtent.Measured -> viewport.width
            is ContentExtent.Fixed -> extent.width
        }
        val allocatedHeight = when (extent) {
            is ContentExtent.Measured -> extent.maxHeight
            is ContentExtent.Fixed -> extent.height
        }

        val stream = Canvas.offscreen(streamWidth, allocatedHeight)
        content.render(stream)

        val streamHeight = when (extent) {
            is ContentExtent.Measured -> stream.contentHeight()
            is ContentExtent.Fixed -> allocatedHeight
        }

        val maxOffsetX = (streamWidth - viewport.width).coerceAtLeast(0)
        val maxOffsetY = (streamHeight - viewport.height).coerceAtLeast(0)

        val focus = stream.focusRect()
        val baseX = offset.x
        val baseY = offset.y

        // Follow only when the focus moved (or a recenter was explicitly asked for); otherwise the
        // given offset stands, so a manual pan/scroll survives every subsequent render.
        val shouldFollow = focus != null && focus != previousFocus

        val offsetX = resolveAxis(baseX, focus?.let { it.x to it.x + it.width }, viewport.width, maxOffsetX, shouldFollow)
        val offsetY = resolveAxis(baseY, focus?.let { it.y to it.y + it.height }, viewport.height, maxOffsetY, shouldFollow)

        viewport.blit(stream, offsetX, offsetY, 0, 0, viewport.width, viewport.height)

        scroll = ScrollState(ScrollOffset(offsetX, offsetY), ScrollOffset(maxOffsetX, maxOffsetY), focus)
    }

    private fun resolveAxis(
        base: Int,
        focusRange: Pair<Int, Int>?,
        viewportSize: Int,
        maxOffset: Int,
        shouldFollow: Boolean,
    ): Int = when {
        focusRange == null -> base.coerceIn(0, maxOffset)
        recenter -> ScrollFollow.center(focusRange.first, focusRange.second, viewportSize, maxOffset)
        shouldFollow -> ScrollFollow.follow(base, focusRange.first, focusRange.second, viewportSize, maxOffset)
        else -> base.coerceIn(0, maxOffset)
    }
}

/**
 * Composes a bordered, scrolling panel: [Bordered] for the box, [Scrolled] for the scroll math,
 * and [Padded] for the reclaimable top spacer row — the single construction site every scrolling
 * panel (side panels and the tactical board alike) goes through, so the three decorators are
 * wired together exactly once.
 *
 * The vertical component of [Bordered.PADDING] is folded into the content stream (via [Padded],
 * and — for [ContentExtent.Fixed] — into the extent's height) rather than the viewport, which is
 * what makes it a spacer at rest that the content reclaims the moment the view scrolls. The
 * horizontal component becomes [gutters][Bordered], a pure viewport concern.
 */
internal fun scrollingPanel(
    title: String,
    badge: String?,
    content: View,
    extent: ContentExtent,
    offset: ScrollOffset = ScrollOffset.ZERO,
    previousFocus: FocusRect? = null,
    recenter: Boolean = false,
): Bordered {
    val verticalPadding = Bordered.PADDING.vertical()
    val paddedContent = Padded(verticalPadding, content)
    val paddedExtent = when (extent) {
        is ContentExtent.Measured -> extent
        is ContentExtent.Fixed -> ContentExtent.Fixed(extent.width, extent.height + verticalPadding.top + verticalPadding.bottom)
    }
    val scrolled = Scrolled(paddedContent, paddedExtent, offset, previousFocus, recenter)
    return Bordered(
        content = scrolled,
        title = title,
        badge = badge,
        gutters = Bordered.PADDING.horizontal(),
        thumbsFrom = scrolled,
    )
}
