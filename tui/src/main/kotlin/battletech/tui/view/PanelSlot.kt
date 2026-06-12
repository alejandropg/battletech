package battletech.tui.view

/**
 * Per-frame description of one side panel: where it is in the layout ([width]),
 * what it is ([index]/[title]), whether the user has collapsed it, the current
 * scroll offset (null = anchored default), the bottom-anchor flag, and how to
 * build its full content view on demand.
 *
 * [buildReal] is only invoked when the panel is expanded, so any data gathering
 * for the full view is skipped while collapsed.
 */
public class PanelSlot(
    public val index: Int,
    public val width: Int,
    public val title: String,
    public val collapsed: Boolean,
    public val scrollOffset: Int? = null,
    public val anchorBottom: Boolean = false,
    public val buildReal: () -> View?,
)

/**
 * Single source of the collapsed-vs-expanded decision for every panel. Returns
 * `null` when the slot has no width (not visible) so callers can skip it.
 *
 * Expanded panels are wrapped in [ScrollablePanelView] so scrolling, offset
 * clamping, and scrollbar rendering are handled generically.
 */
public fun resolvePanel(slot: PanelSlot): View? = when {
    slot.width <= 0 -> null
    slot.collapsed -> CollapsedPanelView(slot.index, slot.title)
    else -> {
        val content = slot.buildReal() ?: return null
        ScrollablePanelView(slot.index, slot.title, content, slot.scrollOffset, slot.anchorBottom)
    }
}
