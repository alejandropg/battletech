package tenter.view

import tenter.screen.Canvas
import tenter.screen.RevealRect

/**
 * A bordered, scrolling panel — [Bordered] for the box and [Viewport] for the scroll math, wired
 * together by [scrollingPanel]. Exists as its own type (rather than callers just getting a
 * [Bordered] back) so the settled [scroll] state has somewhere to live that isn't the border
 * decorator: a border knows nothing about scrolling in general, only how to draw thumbs for
 * whichever [Viewport] it was told about — see [Bordered.thumbsFrom].
 */
public class ScrollingPanel internal constructor(
    private val bordered: Bordered,
    private val viewport: Viewport,
) : View {

    /** What this panel's content actually settled on this render — see [ScrollState]. */
    public val scroll: ScrollState get() = viewport.scroll

    override fun draw(canvas: Canvas) {
        bordered.draw(canvas)
    }
}

/**
 * Composes a [ScrollingPanel]: [Bordered] for the box, [Viewport] for the scroll math, and
 * [Padded] for the reclaimable top spacer row — the single construction site every scrolling
 * panel goes through, so the three decorators are wired together exactly once.
 *
 * The vertical component of [Bordered.PADDING] is folded into the content stream (via [Padded],
 * and — for [ContentExtent.Fixed] — into the extent's height) rather than the viewport, which is
 * what makes it a spacer at rest that the content reclaims the moment the view scrolls. The
 * horizontal component becomes [gutters][Bordered], a pure viewport concern.
 */
public fun scrollingPanel(
    title: String,
    badge: String?,
    content: View,
    extent: ContentExtent,
    offset: ScrollOffset = ScrollOffset.ZERO,
    previousReveal: RevealRect? = null,
    recenter: Boolean = false,
): ScrollingPanel {
    val verticalPadding = Bordered.PADDING.vertical()
    val paddedContent = Padded(verticalPadding, content)
    val paddedExtent = when (extent) {
        is ContentExtent.Measured -> extent
        is ContentExtent.Fixed -> ContentExtent.Fixed(extent.width, extent.height + verticalPadding.top + verticalPadding.bottom)
    }
    val viewport = Viewport(paddedContent, paddedExtent, offset, previousReveal, recenter)
    val bordered = Bordered(
        content = viewport,
        title = title,
        badge = badge,
        gutters = Bordered.PADDING.horizontal(),
        thumbsFrom = viewport,
    )
    return ScrollingPanel(bordered, viewport)
}
