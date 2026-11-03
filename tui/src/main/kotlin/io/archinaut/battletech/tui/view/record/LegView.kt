package io.archinaut.battletech.tui.view.record

import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.widget.Widget

/**
 * A leg's outline on the paper doll: [location]'s pip box, [width] pips to a row over [rows]
 * rows, widening into a foot that reaches two columns out on the [side] away from the center.
 * `width + 4` by `rows + 3` cells, the pip box starting [boxOffset] columns in; its caption is
 * the diagram's to place.
 */
internal class LegView(
    private val location: LocationDiagram.Location,
    private val side: BodySide,
    private val width: Int,
    private val rows: Int,
) : Widget {

    override fun render(canvas: Canvas) {
        val style = location.style
        val x = boxOffset(side)
        canvas.setString(x, 0, topEdge(width), style)
        PipRowsView(location, width, rows).render(canvas.region(Rect(x, 1, width + 2, rows)))
        val transitionY = rows + 1
        when (side) {
            BodySide.LEFT -> {
                canvas.setString(x - 1, transitionY, "╱", style)
                canvas.setString(x + width + 1, transitionY, "│", style)
            }
            BodySide.RIGHT -> {
                canvas.setString(x, transitionY, "│", style)
                canvas.setString(x + width + 2, transitionY, "╲", style)
            }
            BodySide.CENTER -> error("a leg must be left or right")
        }
        canvas.setString(0, transitionY + 1, bottomEdge(width + 2), style)
    }

    internal companion object {
        /** Columns between the view's left edge and its pip box: the foot's reach on the left. */
        internal fun boxOffset(side: BodySide): Int = if (side == BodySide.LEFT) 2 else 0
    }
}
