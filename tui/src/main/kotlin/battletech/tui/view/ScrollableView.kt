package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.FocusRect

/** A 2D scroll offset, in characters. */
internal data class ScrollOffset(val x: Int = 0, val y: Int = 0) {
    operator fun plus(other: ScrollOffset): ScrollOffset = ScrollOffset(x + other.x, y + other.y)

    companion object {
        val ZERO: ScrollOffset = ScrollOffset(0, 0)
    }
}

/**
 * What a [ScrollableView] actually rendered at: the offset it settled on, each axis's max, and the
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
 * How much content a [ScrollableView] must accommodate, and how to find out.
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
 * The one place in the TUI that knows how to scroll: chrome, an offscreen content stream,
 * clamping, blitting, and per-axis scrollbars — used identically by side panels (vertical,
 * measured content) and the tactical board (both axes, fixed content).
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
internal class ScrollableView(
    private val title: String,
    private val badge: String?,
    private val content: View,
    private val extent: ContentExtent,
    private val offset: ScrollOffset = ScrollOffset.ZERO,
    private val previousFocus: FocusRect? = null,
    private val recenter: Boolean = false,
) : View {

    /** What was actually rendered — the effective offset and each axis's max. Set by [render]. */
    var state: ScrollState = ScrollState.NONE
        private set

    override fun render(canvas: Canvas) {
        val viewport = PanelChrome.drawScrollable(canvas, title, badge)
        if (viewport.width <= 0 || viewport.height <= 0) {
            state = ScrollState.NONE
            return
        }

        val padding = PanelChrome.PADDING.vertical()
        val streamWidth = when (extent) {
            is ContentExtent.Measured -> viewport.width
            is ContentExtent.Fixed -> extent.width
        }
        val allocatedHeight = when (extent) {
            is ContentExtent.Measured -> extent.maxHeight
            is ContentExtent.Fixed -> extent.height + padding.top + padding.bottom
        }

        // The top padding row is reserved inside the stream, not baked into the viewport inset,
        // so it reads as a spacer at rest and content reclaims it the moment the view scrolls.
        val stream = Canvas.offscreen(streamWidth, allocatedHeight)
        content.render(stream.inset(padding))

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

        drawThumb(canvas, vertical = true, track = viewport.height, contentSize = streamHeight, viewportSize = viewport.height, offset = offsetY)
        drawThumb(canvas, vertical = false, track = viewport.width, contentSize = streamWidth, viewportSize = viewport.width, offset = offsetX)

        state = ScrollState(ScrollOffset(offsetX, offsetY), ScrollOffset(maxOffsetX, maxOffsetY), focus)
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

    private fun drawThumb(canvas: Canvas, vertical: Boolean, track: Int, contentSize: Int, viewportSize: Int, offset: Int) {
        val thumbRange = Scrollbar.thumb(track, contentSize, viewportSize, offset) ?: return
        for (i in thumbRange) {
            if (vertical) {
                canvas.set(canvas.width - 1, PanelChrome.VIEWPORT_INSET.top + i, Cell("▐", GREEN_STYLE))
            } else {
                canvas.set(PanelChrome.VIEWPORT_INSET.left + i, canvas.height - 1, Cell("▬", GREEN_STYLE))
            }
        }
    }

    private companion object {
        private val GREEN_STYLE = Cell.Style(Color.GREEN)
    }
}
