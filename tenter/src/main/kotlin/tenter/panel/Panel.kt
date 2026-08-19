package tenter.panel

import tenter.screen.Canvas
import tenter.screen.ChromeRole
import tenter.screen.RevealRect
import tenter.view.ContentExtent
import tenter.view.ScrollOffset
import tenter.view.View
import tenter.view.scrollingPanel

/** Stable identity for a [Panel]: what [Panel.render] shows as its `[badge]` in its border. */
public interface PanelId {
    public val badge: Char
}

/**
 * One panel: stable [id] and [title], the per-[PanelState] view builders, and — unlike a
 * stateless descriptor — the user's own display preference for it. [state] and its scroll
 * position are choices only this panel's own events ever change and only its own rendering ever
 * reads, so they live here rather than in the host application's frame state — see [render].
 *
 * A [Panel] declares up to three views, one per [PanelState] — [normal] is mandatory, [minimized]
 * and [maximized] are optional. [states] lists exactly the declared ones, and [cycleState] walks
 * only those. A panel carries no VISIBILITY logic — deciding what's shown this frame is the host
 * application's job — and this class never sees that state directly; it builds from the prepared
 * [I] the caller hands to [render]. That split is deliberate: visibility is decided by things a
 * panel cannot observe, so it is derived fresh every frame and never stored; state and scroll are
 * decided by nothing but this panel's own events, so they persist here across frames with no
 * round trip through the host's state.
 *
 * One [Panel] instance is meant to live for exactly one screen's whole lifetime, never longer —
 * a fresh registry of panels per screen, never a global singleton, so one test's panel state can
 * never leak into another's.
 */
public class Panel<K : PanelId, I>(
    public val id: K,
    public val title: String,
    private val normalWidth: Int,
    private val extent: (I) -> ContentExtent = { ContentExtent.Measured() },
    private val normal: (I) -> View?,
    private val minimized: ((I) -> View?)? = null,
    private val maximized: ((I) -> View?)? = null,
) {
    private var scroll = ScrollOffset.ZERO
    private var lastReveal: RevealRect? = null
    private var restoreState: PanelState = PanelState.NORMAL

    public var state: PanelState = PanelState.NORMAL
        private set

    /** The declared states, smallest first — what [cycleState] walks. Always contains NORMAL. */
    public val states: List<PanelState> =
        listOfNotNull(
            minimized?.let { PanelState.MINIMIZED },
            PanelState.NORMAL,
            maximized?.let { PanelState.MAXIMIZED },
        )

    /** Column width for MINIMIZED/NORMAL. Never consulted for MAXIMIZED — the layout supplies that. */
    public val width: Int get() = if (state == PanelState.MINIMIZED) MINIMIZED_WIDTH else normalWidth

    /** The offset this panel settled on in its last render — the board's click-to-hex mirror reads this. */
    public val offset: ScrollOffset get() = scroll

    /** Steps [delta] through [states] (+1 forward, -1 backward), wrapping. A no-op for a single-state panel. */
    public fun cycleState(delta: Int) {
        val index = states.indexOf(state)
        val next = states[(index + delta).mod(states.size)]
        if (next == PanelState.MAXIMIZED) restoreState = state
        state = next
    }

    /** Returns to the state this panel was maximized from. No-op unless currently [PanelState.MAXIMIZED]. */
    public fun demoteFromMaximized() {
        if (state == PanelState.MAXIMIZED) state = restoreState
    }

    public fun scrollBy(dx: Int, dy: Int) {
        scroll = ScrollOffset(scroll.x + dx, scroll.y + dy)
    }

    /**
     * Renders this panel's chrome and content into [canvas], absorbing whatever scroll offset and
     * reveal rect it settles on — the next call picks up right where this one left off. No-op if
     * the current state's builder declines to build content (e.g. its per-frame data is
     * momentarily absent). Note what that no-op means at [PanelState.MAXIMIZED]: a maximized panel
     * is the ONLY thing in the content region, so declining there leaves that whole region blank
     * rather than the panel-width gap a [PanelState.NORMAL] panel would leave. Declare [maximized]
     * only for a panel whose visibility the host gates on the same data its builder needs.
     *
     * [focused] colors the border/title/scrollbar-thumb green (via
     * [tenter.screen.ChromeRole.PANEL_BORDER_FOCUSED]) instead of the neutral
     * [tenter.screen.ChromeRole.PANEL_BORDER].
     *
     * [forgetReveal] is a one-shot override for the resize case: the viewport just changed size, so
     * this render should treat any content reveal as freshly arrived (auto-follow into view) rather
     * than compare it against [lastReveal] from before the resize. The settled reveal this render
     * still becomes [lastReveal] for the next call — it is not a permanent reset.
     *
     * [recenter] is a one-shot request (see [PanelSet.requestRecenter]) to recenter on this
     * panel's reveal target regardless of whether it moved.
     */
    public fun render(
        canvas: Canvas,
        inputs: I,
        focused: Boolean,
        forgetReveal: Boolean = false,
        recenter: Boolean = false,
    ) {
        val builder = when (state) {
            PanelState.MINIMIZED -> minimized ?: error("Panel $id is in MINIMIZED state but declares no minimized view")
            PanelState.NORMAL -> normal
            PanelState.MAXIMIZED -> maximized ?: error("Panel $id is in MAXIMIZED state but declares no maximized view")
        }
        val content = builder(inputs) ?: return
        val role = if (focused) ChromeRole.PANEL_BORDER_FOCUSED else ChromeRole.PANEL_BORDER
        val panel = scrollingPanel(
            title = title,
            badge = id.badge.toString(),
            content = content,
            extent = extent(inputs),
            offset = scroll,
            previousReveal = if (forgetReveal) null else lastReveal,
            recenter = recenter,
            borderColor = role,
            titleColor = role,
        )
        panel.draw(canvas)
        scroll = panel.scroll.offset
        lastReveal = panel.scroll.revealed
    }

    public companion object {
        /** Column width of a minimized panel: just enough for its `[badge]` and one letter of title per row. */
        public const val MINIMIZED_WIDTH: Int = 7
    }
}
