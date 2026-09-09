package tenter.panel

import tenter.screen.Canvas
import tenter.screen.ChromeRole
import tenter.view.ContentView
import tenter.view.ScrollOffset
import tenter.view.ViewportState
import tenter.view.scrollingPanel

/** Stable identity for a [Panel] — a bare marker interface for the [Panel]'s [K] type parameter. */
public interface PanelId

/**
 * One panel: stable [id] and [title], the per-[PanelState] view builders, and — unlike a
 * stateless descriptor — the user's own display preference for it. [state] and its scroll
 * position are choices only this panel's own events ever change and only its own rendering ever
 * reads, so they live here rather than in the host application's frame state — see [render].
 *
 * A [Panel] declares up to three views, one per [PanelState] — [normal] is mandatory, [minimized]
 * and [maximized] are optional. [states] lists exactly the declared ones, and [cycleState] walks
 * only those. Every declared builder must return a view: "there is nothing to show this frame" is
 * a VISIBILITY decision, and a panel carries no visibility logic — deciding what's shown is the
 * host application's job, and this class never sees that state directly; it builds from the
 * prepared [I] the caller hands to [render]. A builder that cannot build from the [I] it is given
 * means the host showed a panel it should have hidden, which is a bug in the host's visibility
 * rule rather than a case for this class to absorb. That split is deliberate: visibility is decided by things a
 * panel cannot observe, so it is derived fresh every frame and never stored; state and scroll are
 * decided by nothing but this panel's own events, so they persist here across frames with no
 * round trip through the host's state.
 * Each state supplies prepared content. Fixed-size raw views are wrapped with
 * [tenter.view.fixedContent] at the application seam, while flowing views use
 * [tenter.view.contentView]. The panel owns one [ViewportState] for its entire lifetime.
 *
 * One [Panel] instance is meant to live for exactly one screen's whole lifetime, never longer —
 * a fresh registry of panels per screen, never a global singleton, so one test's panel state can
 * never leak into another's.
 */
public class Panel<K : PanelId, I>(
    public val id: K,
    public val title: String,
    private val normalWidth: Int,
    private val badge: Char? = null,
    private val normal: (I) -> ContentView,
    private val minimized: ((I) -> ContentView)? = null,
    private val minimizedWidth: ((I) -> Int)? = null,
    private val maximized: ((I) -> ContentView)? = null,
) {
    private val viewportState: ViewportState = ViewportState()
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

    /** Default column width when no input-aware minimized width is supplied. */
    public val width: Int get() = if (state == PanelState.MINIMIZED) MINIMIZED_WIDTH else normalWidth

    /** Resolves this panel's column width for the current inputs; MAXIMIZED is sized by the layout. */
    public fun widthFor(inputs: I): Int = when (state) {
        PanelState.MINIMIZED -> (minimizedWidth?.invoke(inputs) ?: MINIMIZED_WIDTH).coerceAtLeast(MINIMIZED_WIDTH)
        PanelState.NORMAL, PanelState.MAXIMIZED -> normalWidth
    }

    /** The offset this panel settled on in its last render — a host mapping a screen click back onto this panel's content reads this. */
    public val offset: ScrollOffset get() = viewportState.settled?.offset ?: ScrollOffset.ZERO

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
        viewportState.scrollBy(dx, dy)
    }

    /**
     * Renders this panel's chrome and content into [canvas], absorbing whatever scroll offset and
     * reveal rect it settles on — the next call picks up right where this one left off. Always
     * draws: the current state's builder returns a view, and whether this panel should be on
     * screen at all was already decided by the host — see this class's KDoc.
     *
     * [focused] colors the border/title/scrollbar-thumb green (via
     * [tenter.screen.ChromeRole.PANEL_BORDER_FOCUSED]) instead of the neutral
     * [tenter.screen.ChromeRole.PANEL_BORDER].
     *
     * [recenter] is a one-shot request (see [PanelSet.requestRecenter]) to recenter on this
     * panel's reveal target regardless of whether it moved.
     */
    public fun render(
        canvas: Canvas,
        inputs: I,
        focused: Boolean,
        recenter: Boolean = false,
    ) {
        val builder = when (state) {
            PanelState.MINIMIZED -> minimized ?: error("Panel $id is in MINIMIZED state but declares no minimized view")
            PanelState.NORMAL -> normal
            PanelState.MAXIMIZED -> maximized ?: error("Panel $id is in MAXIMIZED state but declares no maximized view")
        }
        val content = builder(inputs)
        if (recenter) viewportState.requestRecenter()
        val role = if (focused) ChromeRole.PANEL_BORDER_FOCUSED else ChromeRole.PANEL_BORDER
        val panel = scrollingPanel(
            title = title,
            badge = badge?.toString(),
            content = content,
            state = viewportState,
            borderColor = role,
            titleColor = role,
        )
        panel.draw(canvas)
    }

    public companion object {
        /** Column width of a minimized panel: just enough for its `[badge]` and one letter of title per row. */
        public const val MINIMIZED_WIDTH: Int = 7
    }
}
