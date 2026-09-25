package tenter.view

import tenter.screen.Canvas
import tenter.palette.ChromeRole
import tenter.palette.ColorRole

/**
 * A bordered, scrolling panel — [Bordered] for the box and [Viewport] for the scroll math, wired
 * together by [scrollingPanel]. Exists as its own type (rather than callers just getting a
 * [Bordered] back) because painting the scrollbar thumbs has to happen *after* the viewport has
 * settled, and a border decorator knows nothing about scrolling. Read the settled result from the
 * [ViewportState] you supplied, not from this view.
 */
public class ScrollingPanel internal constructor(
    private val bordered: Bordered,
    private val viewport: Viewport,
) : View {

    override fun draw(canvas: Canvas) {
        bordered.draw(canvas)
        viewport.settled?.let { bordered.drawThumbs(canvas, it) }
    }
}

/**
 * Composes a [ScrollingPanel]: [Bordered] for the box, [Viewport] for the scroll math, and
 * [Padded] for the reclaimable top spacer row — the single construction site every scrolling
 * panel goes through, so the three decorators are wired together exactly once.
 *
 * The vertical component of [Bordered.PADDING] is folded into the content stream via [Padded]
 * rather than the viewport, which is
 * what makes it a spacer at rest that the content reclaims the moment the view scrolls. The
 * horizontal component becomes [gutters][Bordered], a pure viewport concern.
 */
public fun scrollingPanel(
    title: String,
    badge: String?,
    content: ContentView,
    state: ViewportState = ViewportState(),
    borderColor: ColorRole = ChromeRole.PANEL_BORDER,
    titleColor: ColorRole = ChromeRole.ACCENT,
): ScrollingPanel {
    val paddedContent = Padded.prepared(Bordered.PADDING.vertical(), content)
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
