package tenter.panel

import tenter.screen.Canvas
import tenter.screen.FocusRect
import tenter.view.ContentExtent
import tenter.view.ScrollOffset
import tenter.view.View
import tenter.view.scrollingPanel

/** Stable identity for a [Panel]: what [Panel.render] shows as its `[badge]` when collapsed or bordered. */
public interface PanelKey {
    public val badge: Char
}

/**
 * One side panel: stable [id] and [title], its expanded [width], how to [build] its view from
 * the per-frame [I] inputs — and, unlike a stateless descriptor, the user's own display
 * preference for it. [collapsed] (shrink to a stub) and its scroll position are choices only
 * this panel's own events ever change and only its own rendering ever reads, so they live here
 * rather than in the host application's frame state — see [render].
 *
 * A [Panel] carries no VISIBILITY logic — deciding what's shown this frame is the host
 * application's job, from whatever state it tracks — and this class never sees that state
 * directly; it builds from the prepared [I] the caller hands to [render]. That split is
 * deliberate: visibility is decided by things a panel cannot observe, so it is derived fresh
 * every frame and never stored; collapse and scroll are decided by nothing but this panel's own
 * events, so they persist here across frames with no round trip through the host's state.
 *
 * One [Panel] instance is meant to live for exactly one screen's whole lifetime, never longer —
 * a fresh registry of panels per screen, never a global singleton, so one test's collapsed/
 * scrolled panel can never leak into another's.
 */
public class Panel<K : PanelKey, I>(
    public val id: K,
    public val title: String,
    private val expandedWidth: Int,
    private val build: (I) -> View?,
) {
    private var scroll = ScrollOffset.ZERO
    private var lastFocus: FocusRect? = null

    /** Whether the user has shrunk this panel to its stub — see [toggleCollapsed]. */
    public var collapsed: Boolean = false
        private set

    /** This panel's column width right now — [expandedWidth], or [COLLAPSED_WIDTH] while [collapsed]. */
    public val width: Int get() = if (collapsed) COLLAPSED_WIDTH else expandedWidth

    public fun toggleCollapsed() {
        collapsed = !collapsed
    }

    public fun scrollBy(delta: Int) {
        scroll = ScrollOffset(scroll.x, scroll.y + delta)
    }

    /**
     * Renders this panel's chrome and content into [canvas], absorbing whatever scroll offset and
     * focus rect it settles on — the next call picks up right where this one left off. No-op if
     * [build] declines to build content for an expanded panel (e.g. its per-frame data is
     * momentarily absent); a collapsed panel always has content (its own title).
     *
     * [forgetFocus] is a one-shot override for the resize case: the viewport just changed size, so
     * this render should treat any content focus as freshly arrived (auto-follow into view) rather
     * than compare it against [lastFocus] from before the resize. The settled focus this render
     * still becomes [lastFocus] for the next call — it is not a permanent reset.
     */
    public fun render(canvas: Canvas, inputs: I, forgetFocus: Boolean = false) {
        if (collapsed) {
            CollapsedPanelView(id.badge, title).render(canvas)
            return
        }
        val content = build(inputs) ?: return
        val panel = scrollingPanel(
            title = title,
            badge = id.badge.toString(),
            content = content,
            extent = ContentExtent.Measured(),
            offset = scroll,
            previousFocus = if (forgetFocus) null else lastFocus,
        )
        panel.render(canvas)
        scroll = panel.scroll.offset
        lastFocus = panel.scroll.focus
    }

    public companion object {
        /** Column width of a collapsed panel: just enough for its `[badge]` and one letter of title per row. */
        public const val COLLAPSED_WIDTH: Int = 7
    }
}
