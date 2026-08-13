package tenter.panel

/**
 * The main content rect and every visible [Panel]'s placement in one frame — recomputed by
 * [compute] each call. Panels are laid out right-to-left, filling the space to the right of the
 * main content area; [contentX]/[contentY]/[contentWidth]/[contentHeight] describe what's left.
 */
public class PanelLayout<K : PanelId, I> private constructor(
    public val contentX: Int,
    public val contentY: Int,
    public val contentWidth: Int,
    public val contentHeight: Int,
    public val slots: List<Slot<K, I>>,
) {
    /** One panel's placement this frame: which [panel], and its left column [x] (its width is `panel.width`). */
    public data class Slot<K : PanelId, I>(val panel: Panel<K, I>, val x: Int)

    /**
     * The expanded (non-collapsed) [Slot] at screen column [x], row [y], or `null` if none
     * matches. Only rows `contentY until contentY + contentHeight` are considered; a click
     * outside that band (e.g. on a status bar above it) returns null.
     */
    public fun slotAt(x: Int, y: Int): Slot<K, I>? {
        if (y < contentY || y >= contentY + contentHeight) return null
        return slots.firstOrNull { slot -> !slot.panel.collapsed && x >= slot.x && x < slot.x + slot.panel.width }
    }

    public companion object {
        /**
         * Lays out [visible] panels along the right edge of a [width]x[height] screen, reserving
         * [reservedTop] rows at the top (e.g. for a status bar) above the content area.
         */
        public fun <K : PanelId, I> compute(
            width: Int,
            height: Int,
            reservedTop: Int,
            visible: List<Panel<K, I>>,
        ): PanelLayout<K, I> {
            val totalPanelWidth = visible.sumOf { it.width }
            val contentWidth = width - totalPanelWidth
            val contentHeight = height - reservedTop

            val slots = buildList {
                var nextX = contentWidth
                for (panel in visible) {
                    add(Slot(panel, nextX))
                    nextX += panel.width
                }
            }
            return PanelLayout(contentX = 0, contentY = reservedTop, contentWidth = contentWidth, contentHeight = contentHeight, slots = slots)
        }
    }
}
