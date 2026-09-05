package battletech.tui.animation

import tenter.animation.Animation
import tenter.animation.AnimationSize
import tenter.view.Bordered

/** A panel's absolute top-left on screen, border included. */
internal data class PanelPlacement(val x: Int, val y: Int)

/** The outer dimensions of this animation when wrapped in a [Bordered] panel. */
internal val AnimationSize.panelSize: AnimationSize
    get() = AnimationSize(
        width = width + Bordered.BORDER.left + Bordered.BORDER.right,
        height = height + Bordered.BORDER.top + Bordered.BORDER.bottom,
    )

/**
 * Places up to 3 animation panels at FIXED offsets from the screen centre. Which
 * panel lands where is determined entirely by its slot (painting order "1, 2, 3" — see [OFFSETS])
 * — there is no randomness here, and no attempt to avoid panels overlapping one another; only
 * staying fully on-screen, with a minimum edge margin, is enforced.
 *
 * Each offset is a `(dx, dy)` cell delta added to the screen centre to get the panel's top-left
 * corner:
 *
 * ```
 *            ╭───╮
 *            │ 1 │               1: above centre, slightly left
 *            ╰───╯
 *                       ╭───╮
 *                 O     │ 3 │    3: right of centre, straddling it vertically
 *      ╭───╮            ╰───╯
 *      │ 2 │                     2: lower-left of centre
 *      ╰───╯
 * ```
 *
 * A panel's raw offset is used as-is whenever the screen has room; it is only pulled inward —
 * clamped to keep [EDGE_MARGIN] cells clear of whichever edge it would otherwise cross — on a
 * screen too small for the full shape, which is what "move the panels inward on a small screen"
 * amounts to. That margin itself only shrinks below [EDGE_MARGIN] once the screen can't spare it,
 * down to 0 on a screen that barely fits the panel at all, but the panel is always fully on-screen.
 * Panels are free to overlap each other; only the screen edge is guarded.
 */
internal object AnimationLayout {

    /** Minimum cells kept clear between a panel and the screen edge it sits nearest to. */
    private const val EDGE_MARGIN: Int = 4

    private data class Offset(val dx: Int, val dy: Int)

    /**
     * Slot 0/1/2's fixed offset from screen centre, in painting order — "1" is drawn first
     * (bottom), "3" last (top). Anchors the panel's top-left corner.
     */
    private val OFFSETS: List<Offset> = listOf(
        Offset(dx = -49, dy = -24), // 1: above centre, slightly left
        Offset(dx = -73, dy = 4),   // 2: lower-left of centre
        Offset(dx = 7, dy = -1),    // 3: right of centre
    )

    /**
     * One placement per [animations] entry (1..3), using the first entries of [OFFSETS] in order.
     * Every panel is fully on-screen. Empty when any panel does not fit at all — the caller then
     * draws nothing for the whole volley.
     */
    fun place(
        animations: List<Animation>,
        screenWidth: Int,
        screenHeight: Int,
    ): List<PanelPlacement> {
        require(animations.size in 1..OFFSETS.size) {
            "animations must contain 1..${OFFSETS.size} entries, was ${animations.size}"
        }
        val panelSizes = animations.map { it.size.panelSize }
        if (panelSizes.any { it.width > screenWidth || it.height > screenHeight }) return emptyList()

        val screenCenterX = screenWidth / 2
        val screenCenterY = screenHeight / 2
        return panelSizes.zip(OFFSETS).map { (panelSize, offset) ->
            // Shrinks below EDGE_MARGIN only once the screen can't spare it, never below 0 — the
            // fit check above already guarantees enough slack for these bounds.
            PanelPlacement(
                x = axis(
                    screenCenterX, offset.dx, screenWidth, panelSize.width,
                    minOf(EDGE_MARGIN, (screenWidth - panelSize.width) / 2),
                ),
                y = axis(
                    screenCenterY, offset.dy, screenHeight, panelSize.height,
                    minOf(EDGE_MARGIN, (screenHeight - panelSize.height) / 2),
                ),
            )
        }
    }

    /**
     * A panel's top-left on one axis: [center] + [delta], pulled inward just enough to keep
     * [margin] cells clear of both screen edges. The clamp range is always non-empty —
     * `margin <= screenSize - panelSize - margin` — because [place]'s fit check and its
     * `minOf(EDGE_MARGIN, …)` margin already guarantee at least that much slack.
     */
    private fun axis(center: Int, delta: Int, screenSize: Int, panelSize: Int, margin: Int): Int =
        (center + delta).coerceIn(margin, screenSize - panelSize - margin)
}
