package io.archinaut.tenter.panel

import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.view.ContentView
import io.archinaut.tenter.view.ViewportState
import io.archinaut.tenter.view.scrollingPanel

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
    internal val id: K,
    private val title: String,
    private val badge: String? = null,
    private val normal: (I) -> Presentation,
    private val minimized: ((I) -> Presentation)? = null,
    private val maximized: ((I) -> Presentation)? = null,
) {
    /** Prepared content and its sizing preference for one rendered state. */
    public class Presentation private constructor(
        public val content: ContentView,
        private val preferredWidth: Int?,
    ) {
        internal fun requiredWidth(): Int = requireNotNull(preferredWidth) {
            "A side, minimized, or configured fixed-width panel requires Presentation.fixedWidth"
        }

        public companion object {
            /** Uses the space assigned to a main, proportional, or maximized panel. */
            public fun allocated(content: ContentView): Presentation = Presentation(content, null)

            /**
             * Preferred outer width for a side, minimized, or configured fixed-width panel.
             * Clipped to available space. Main, proportional, and maximized slots always use
             * their allocated width, allowing a declaration to be reused across layout modes.
             */
            public fun fixedWidth(content: ContentView, width: Int): Presentation {
                require(width > 0) { "Panel presentation width must be positive: $width" }
                return Presentation(content, width)
            }
        }
    }

    private val viewportState: ViewportState = ViewportState()
    private var restoreState: PanelState = PanelState.NORMAL
    private var attached: Boolean = false

    internal var state: PanelState = PanelState.NORMAL
        private set

    /** The declared states, smallest first — what the owning set cycles. */
    internal val states: List<PanelState> = listOfNotNull(
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
    ): ViewportState {
        val candidate = viewportState.fork()
        val role = if (focused) ChromeRole.PANEL_BORDER_FOCUSED else ChromeRole.PANEL_BORDER
        val scrollingPanel = scrollingPanel(
            title = title,
            badge = badge,
            content = presentation.content,
            state = candidate,
            borderColor = role,
            titleColor = role,
        )
        scrollingPanel.draw(canvas)
        return candidate
    }

    internal fun settle(candidate: ViewportState) {
        viewportState.adopt(candidate)
    }

    public companion object {
        /** Column width of a minimized panel when an application has no narrower preference. */
        public const val MINIMIZED_WIDTH: Int = 7
    }
}
