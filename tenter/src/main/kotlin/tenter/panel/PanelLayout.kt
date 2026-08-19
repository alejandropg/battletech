package tenter.panel

/**
 * The main content rect and every placed [Panel]'s rect in one frame — recomputed by [compute]
 * each call. Ordinarily [main] takes the space left of the side panels, laid out left-to-right
 * along the right edge; when a side panel is [PanelState.MAXIMIZED] it becomes the sole slot,
 * covering the whole content region, and [main] is `null`.
 */
public class PanelLayout<K : PanelId, I> private constructor(
    /** The whole region below the reserved top rows — where a maximized panel goes. */
    public val contentX: Int,
    public val contentY: Int,
    public val contentWidth: Int,
    public val contentHeight: Int,
    /** The main panel's rect, or null when a side panel is maximized and owns the whole region. */
    public val main: Slot<K, I>?,
    public val sides: List<Slot<K, I>>,
) {
    /** One panel's placement this frame. */
    public data class Slot<K : PanelId, I>(
        public val panel: Panel<K, I>,
        public val x: Int,
        public val y: Int,
        public val width: Int,
        public val height: Int,
    )

    /**
     * The SIDE [Slot] at screen column [x], row [y], or `null` if none matches. The main slot is
     * deliberately never returned — see the mouse rules that rely on this.
     *
     * A [PanelState.MINIMIZED] stub IS returned, unlike the collapsed panels this replaced: it is
     * a legitimate (if inert) scroll target, and returning it keeps a click on the stub from
     * falling through to whatever hit-tests the region behind it.
     */
    public fun sideAt(x: Int, y: Int): Slot<K, I>? =
        sides.firstOrNull { slot -> x >= slot.x && x < slot.x + slot.width && y >= slot.y && y < slot.y + slot.height }

    public companion object {
        /**
         * Lays out [sides] and [main] over a [width]x[height] screen, reserving [reservedTop] rows
         * at the top (e.g. for a status bar) above the content area.
         */
        public fun <K : PanelId, I> compute(
            width: Int,
            height: Int,
            reservedTop: Int,
            main: Panel<K, I>,
            sides: List<Panel<K, I>>,
        ): PanelLayout<K, I> {
            val contentX = 0
            val contentY = reservedTop
            val contentHeight = height - reservedTop

            val maximizedSide = sides.firstOrNull { it.state == PanelState.MAXIMIZED }
            if (maximizedSide != null) {
                return PanelLayout(
                    contentX = contentX,
                    contentY = contentY,
                    contentWidth = width,
                    contentHeight = contentHeight,
                    main = null,
                    sides = listOf(Slot(maximizedSide, contentX, contentY, width, contentHeight)),
                )
            }

            val totalSideWidth = sides.sumOf { it.width }
            val mainWidth = width - totalSideWidth
            val mainSlot = Slot(main, 0, contentY, mainWidth, contentHeight)

            val slots = buildList {
                var nextX = mainWidth
                for (panel in sides) {
                    add(Slot(panel, nextX, contentY, panel.width, contentHeight))
                    nextX += panel.width
                }
            }

            return PanelLayout(
                contentX = contentX,
                contentY = contentY,
                contentWidth = width,
                contentHeight = contentHeight,
                main = mainSlot,
                sides = slots,
            )
        }
    }
}
