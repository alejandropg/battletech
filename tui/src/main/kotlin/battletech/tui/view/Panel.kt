package battletech.tui.view

import battletech.tui.game.PanelId
import battletech.tui.screen.Canvas
import battletech.tui.screen.FocusRect

/**
 * One side panel: stable [id] and [title], its expanded [width], how to [build] its view from the
 * per-frame [PanelInputs] — and, unlike a stateless descriptor, the user's own display preference
 * for it. [collapsed] (shrink to a stub) and its scroll position are choices only this panel's own
 * events ever change and only its own rendering ever reads, so they live here rather than in
 * `AppState` — see [render].
 *
 * A [Panel] carries no VISIBILITY logic — [battletech.tui.game.PanelVisibility] decides what's
 * shown this frame, from `AppState` (the phase, game events, the HELP open/close flag) — and never
 * sees `AppState` itself; it builds from the prepared data on [PanelInputs]. That split is
 * deliberate: visibility is decided by things a panel cannot observe, so it is derived fresh every
 * frame and never stored; collapse and scroll are decided by nothing but this panel's own events,
 * so they persist here across frames with no round trip through `AppState`.
 *
 * One [Panel] instance is constructed per [Workspace] (see [Panels.build]) and lives for that
 * workspace's whole lifetime — never a global singleton, so tests building their own [Panels.build]
 * never see another test's collapsed/scrolled state.
 */
internal class Panel(
    val id: PanelId,
    val title: String,
    private val expandedWidth: Int,
    private val build: (PanelInputs) -> View?,
) {
    private var scroll = ScrollOffset.ZERO
    private var lastFocus: FocusRect? = null

    /** Whether the user has shrunk this panel to its stub — see [toggleCollapsed]. */
    var collapsed: Boolean = false
        private set

    /** This panel's column width right now — [expandedWidth], or [COLLAPSED_WIDTH] while [collapsed]. */
    val width: Int get() = if (collapsed) COLLAPSED_WIDTH else expandedWidth

    fun toggleCollapsed() {
        collapsed = !collapsed
    }

    fun scrollBy(delta: Int) {
        scroll = ScrollOffset(scroll.x, scroll.y + delta)
    }

    /**
     * Renders this panel's chrome and content into [canvas], absorbing whatever scroll offset and
     * focus rect it settles on — the next call picks up right where this one left off. No-op if
     * [build] declines to build content for an expanded panel (e.g. its phase-local data is
     * momentarily absent); a collapsed panel always has content (its own title).
     *
     * [forgetFocus] is a one-shot override for the resize case: the viewport just changed size, so
     * this render should treat any content focus as freshly arrived (auto-follow into view) rather
     * than compare it against [lastFocus] from before the resize. The settled focus this render
     * still becomes [lastFocus] for the next call — it is not a permanent reset.
     */
    fun render(canvas: Canvas, inputs: PanelInputs, forgetFocus: Boolean = false) {
        if (collapsed) {
            CollapsedPanelView(id.key, title).render(canvas)
            return
        }
        val content = build(inputs) ?: return
        val bordered = scrollingPanel(
            title = title,
            badge = id.key.toString(),
            content = content,
            extent = ContentExtent.Measured(),
            offset = scroll,
            previousFocus = if (forgetFocus) null else lastFocus,
        )
        bordered.render(canvas)
        scroll = bordered.scroll.offset
        lastFocus = bordered.scroll.focus
    }

    internal companion object {
        /** Column width of a collapsed panel: just enough for its `[key]` badge and one letter of title per row. */
        const val COLLAPSED_WIDTH: Int = 7
    }
}
