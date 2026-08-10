package battletech.tui.view

import battletech.tui.game.PanelId
import battletech.tui.screen.FocusRect

/**
 * One side panel's placement for this frame: which [panel] it is, where it sits ([x], [width]),
 * and whether the user has collapsed it. Carries the registry entry itself — rather than just its
 * [PanelId] — so [pane] can build the panel's content directly, with no separate registry lookup
 * at the call site.
 */
internal data class PlacedPanel(
    val panel: Panel,
    val x: Int,
    val width: Int,
    val collapsed: Boolean,
) {
    val id: PanelId get() = panel.id

    /**
     * Single source of the collapsed-vs-expanded decision for every panel. [scrollOffset] is the
     * vertical offset to restore (null = default, top); [previousFocus] is the focus rect this
     * panel reported last render, fed back so [ScrollableView] auto-follows only on focus
     * movement. Returns `null` when [width] is `0` (not visible) or [Panel.build] declines to
     * build content, so callers can skip it.
     *
     * Expanded panels are wrapped in [ScrollableView] so scrolling, offset clamping, and
     * scrollbar rendering are handled generically.
     */
    fun pane(inputs: PanelInputs, scrollOffset: Int?, previousFocus: FocusRect?): Pane? = when {
        width <= 0 -> null
        collapsed -> CollapsedPanelView(panel.id.key, panel.title)
        else -> {
            val content = panel.build(inputs) ?: return null
            ScrollableView(
                title = panel.title,
                badge = panel.id.key.toString(),
                content = content,
                extent = ContentExtent.Measured(),
                offset = scrollOffset?.let { ScrollOffset(0, it) } ?: ScrollOffset.ZERO,
                previousFocus = previousFocus,
            )
        }
    }
}
