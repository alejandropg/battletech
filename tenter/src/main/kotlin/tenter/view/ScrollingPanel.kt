package tenter.view

import tenter.screen.Canvas
import tenter.screen.ChromeRole
import tenter.screen.ColorRole
import tenter.screen.RevealRect

/**
 * A bordered, scrolling panel — [Bordered] for the box and [Viewport] for the scroll math, wired
 * together by [scrollingPanel]. Exists as its own type (rather than callers just getting a
 * [Bordered] back) so the settled [scroll] state has somewhere to live that isn't the border
 * decorator: a border knows nothing about scrolling in general, only how to draw thumbs from the
 * settled [ScrollState] supplied by this panel after its viewport paints.
 */
public class ScrollingPanel internal constructor(
    private val bordered: Bordered,
    private val viewport: Viewport,
) : View {

    /** What this panel's content actually settled on this render — see [ScrollState]. */
    public val scroll: ScrollState get() = viewport.scroll

    override fun draw(canvas: Canvas) {
        bordered.draw(canvas)
        bordered.drawThumbs(canvas, viewport.scroll)
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
    borderColor: ColorRole = ChromeRole.PANEL_BORDER,
    titleColor: ColorRole = ChromeRole.ACCENT,
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
        borderColor = borderColor,
        titleColor = titleColor,
    )
    return ScrollingPanel(bordered, viewport)
}

/**
 * Prepared-content scrolling panel. The viewport prepares the content once at the actual inner
 * width, while [ViewportState] retains scroll intent and the last completed observation.
 */
public fun scrollingPanel(
    title: String,
    badge: String?,
    content: ContentView,
    state: ViewportState = ViewportState(),
    borderColor: ColorRole = ChromeRole.PANEL_BORDER,
    titleColor: ColorRole = ChromeRole.ACCENT,
): ScrollingPanel {
    val paddedContent = Padded(Bordered.PADDING.vertical(), content)
    val viewport = Viewport(paddedContent, state)
    val bordered = Bordered(
        content = viewport,
        title = title,
        badge = badge,
        gutters = Bordered.PADDING.horizontal(),
        borderColor = borderColor,
        titleColor = titleColor,
    )
    return ScrollingPanel(bordered, viewport)
}
