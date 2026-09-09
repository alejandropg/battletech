package tenter.panel

import tenter.screen.Canvas
import tenter.screen.ChromeRole
import tenter.view.ContentView
import tenter.view.ViewportState
import tenter.view.scrollingPanel

/** Stable identity for a [Panel] — a bare marker interface for the [Panel]'s [K] type parameter. */
public interface PanelId

/**
 * One stateful panel declaration. A panel owns its current state, restore state, attachment, and
 * viewport state; [PanelSet] is the only public module that may mutate those values. Each state
 * builder returns a [Presentation], keeping the prepared content and the width that belongs to it
 * together for one frame.
 *
 * Visibility is still the host application's decision. A declared builder must always return a
 * presentation, while a host chooses which panels to include in a [PanelSet.render] call.
 */
public class Panel<K : PanelId, I>(
    public val id: K,
    public val title: String,
    public val badge: String? = null,
    private val normal: (I) -> Presentation,
    private val minimized: ((I) -> Presentation)? = null,
    private val maximized: ((I) -> Presentation)? = null,
) {
    /** Prepared content and its associated width for one rendered state. */
    public data class Presentation(
        public val content: ContentView,
        public val width: Int,
    ) {
        init {
            require(width >= 0) { "Panel presentation width must not be negative: $width" }
        }
    }

    private val viewportState: ViewportState = ViewportState()
    private var restoreState: PanelState = PanelState.NORMAL
    private var attached: Boolean = false

    internal var state: PanelState = PanelState.NORMAL
        private set

    /** The declared states, smallest first — what the owning set cycles. */
    public val states: List<PanelState> =
        listOfNotNull(
            minimized?.let { PanelState.MINIMIZED },
            PanelState.NORMAL,
            maximized?.let { PanelState.MAXIMIZED },
        )

    /** Steps [delta] through declared states, wrapping while preserving the restore state. */
    internal fun cycleState(delta: Int) {
        val index = states.indexOf(state)
        val next = states[(index + delta).mod(states.size)]
        if (next == PanelState.MAXIMIZED && state != PanelState.MAXIMIZED) restoreState = state
        state = next
    }

    /** Returns to the state this panel was maximized from. */
    internal fun demoteFromMaximized() {
        if (state == PanelState.MAXIMIZED) state = restoreState
    }

    internal fun scrollBy(dx: Int, dy: Int) {
        viewportState.scrollBy(dx, dy)
    }

    internal fun requestRecenter() {
        viewportState.requestRecenter()
    }

    internal fun presentation(inputs: I): Presentation = when (state) {
        PanelState.MINIMIZED -> minimized ?: error("Panel $id is in MINIMIZED state but declares no minimized presentation")
        PanelState.NORMAL -> normal
        PanelState.MAXIMIZED -> maximized ?: error("Panel $id is in MAXIMIZED state but declares no maximized presentation")
    }.invoke(inputs)

    internal fun settledOffset() = viewportState.settled?.offset

    internal fun claimAttachment() {
        require(!attached) { "Panel $id is already attached to a PanelSet" }
        attached = true
    }

    internal fun requireUnattached() {
        require(!attached) { "Panel $id is already attached to a PanelSet" }
    }

    /**
     * Renders the already-selected [presentation] into [canvas]. Selection happens in
     * [PanelSet.render] so the application builder runs exactly once per rendered presentation.
     */
    internal fun render(
        canvas: Canvas,
        presentation: Presentation,
        focused: Boolean,
        recenter: Boolean = false,
    ) {
        if (recenter) viewportState.requestRecenter()
        val role = if (focused) ChromeRole.PANEL_BORDER_FOCUSED else ChromeRole.PANEL_BORDER
        scrollingPanel(
            title = title,
            badge = badge,
            content = presentation.content,
            state = viewportState,
            borderColor = role,
            titleColor = role,
        ).draw(canvas)
    }

    public companion object {
        /** Column width of a minimized panel when an application has no narrower preference. */
        public const val MINIMIZED_WIDTH: Int = 7
    }
}
