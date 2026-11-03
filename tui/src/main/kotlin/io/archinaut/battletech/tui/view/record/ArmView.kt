package io.archinaut.battletech.tui.view.record

import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.widget.Widget

/**
 * An arm's outline on the paper doll: [location]'s pip box, [width] pips to a row over [rows]
 * rows, narrowing by one column toward the hand on the [side] away from the torso.
 * `width + 2` by `rows + 4` cells; its caption is the diagram's to place.
 */
internal class ArmView(
    private val location: LocationDiagram.Location,
    private val side: BodySide,
    private val width: Int,
    private val rows: Int,
) : Widget {

    override fun render(canvas: Canvas) {
        val style = location.style
        canvas.setString(0, 0, topEdge(width), style)
        PipRowsView(location, width, rows).render(canvas.region(Rect(0, 1, width + 2, rows)))
        val transitionY = rows + 1
        val narrowX = when (side) {
            BodySide.LEFT -> {
                canvas.setString(0, transitionY, "│", style)
                canvas.setString(width, transitionY, "╱", style)
                0
            }
            BodySide.RIGHT -> {
                canvas.setString(1, transitionY, "╲", style)
                canvas.setString(width + 1, transitionY, "│", style)
                1
            }
            BodySide.CENTER -> error("an arm must be left or right")
        }
        canvas.setString(narrowX, transitionY + 1, "│", style)
        canvas.setString(narrowX + width, transitionY + 1, "│", style)
        canvas.setString(narrowX, transitionY + 2, bottomEdge(width - 1), style)
    }
}
