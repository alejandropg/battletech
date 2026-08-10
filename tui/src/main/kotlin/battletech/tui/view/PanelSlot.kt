package battletech.tui.view

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
public class PanelSlot internal constructor(
    public val key: Char,
    public val width: Int,
    public val title: String,
    public val collapsed: Boolean,
    public val scrollOffset: Int? = null,
    public val anchorBottom: Boolean = false,
    internal val previousFocus: FocusRect? = null,
    public val buildReal: () -> View?,
)

/**
 * Single source of the collapsed-vs-expanded decision for every panel. Returns
 * `null` when the slot has no width (not visible) so callers can skip it.
 *
 * Expanded panels are wrapped in [ScrollableView] so scrolling, offset
 * clamping, and scrollbar rendering are handled generically.
 */
public fun resolvePanel(slot: PanelSlot): View? = when {
    slot.width <= 0 -> null
    slot.collapsed -> CollapsedPanelView(slot.key, slot.title)
    else -> {
        val content = slot.buildReal() ?: return null
        ScrollableView(
            title = slot.title,
            badge = slot.key.toString(),
            content = content,
            extent = ContentExtent.Measured(),
            offset = slot.scrollOffset?.let { ScrollOffset(0, it) },
            anchorBottom = slot.anchorBottom,
            previousFocus = slot.previousFocus,
        )
    }
}
