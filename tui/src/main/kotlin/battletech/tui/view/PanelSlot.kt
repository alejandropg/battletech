package battletech.tui.view

import battletech.tui.game.PanelId
import battletech.tui.screen.FocusRect

/**
 * Per-frame description of one side panel: where it is in the layout ([width]),
 * what it is ([key]/[title]), whether the user has collapsed it, the current
 * scroll offset (null = anchored default), the bottom-anchor flag, the focus rect
 * this panel reported last render ([previousFocus]), and how to build its full
 * content view on demand.
 *
 * [buildReal] is only invoked when the panel is expanded, so any data gathering
 * for the full view is skipped while collapsed.
 */
internal class PanelSlot(
    val key: PanelId,
    val width: Int,
    val title: String,
    val collapsed: Boolean,
    val scrollOffset: Int? = null,
    val anchorBottom: Boolean = false,
    val previousFocus: FocusRect? = null,
    val buildReal: () -> View?,
)

/**
 * Single source of the collapsed-vs-expanded decision for every panel. Returns
 * `null` when the slot has no width (not visible) so callers can skip it.
 *
 * Expanded panels are wrapped in [ScrollableView] so scrolling, offset
 * clamping, and scrollbar rendering are handled generically.
 */
internal fun resolvePanel(slot: PanelSlot): View? = when {
    slot.width <= 0 -> null
    slot.collapsed -> CollapsedPanelView(slot.key.key, slot.title)
    else -> {
        val content = slot.buildReal() ?: return null
        ScrollableView(
            title = slot.title,
            badge = slot.key.key.toString(),
            content = content,
            extent = ContentExtent.Measured(),
            offset = slot.scrollOffset?.let { ScrollOffset(0, it) },
            anchorBottom = slot.anchorBottom,
            previousFocus = slot.previousFocus,
        )
    }
}
