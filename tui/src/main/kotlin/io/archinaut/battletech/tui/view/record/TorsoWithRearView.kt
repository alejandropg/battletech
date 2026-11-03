package io.archinaut.battletech.tui.view.record

import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Widget

/**
 * A torso section of the armor diagram: the [front] pip box, [frontWidth] pips to a row over
 * [frontRows] rows, open at the bottom into its [rear] track — [rearWidth] by [rearRows], aligned
 * to the outer edge on the [side] it sits on — behind a dashed separator (labelled `REAR` on the
 * center torso), and the rear's `remaining/max` caption below it.
 * `frontWidth + 2` by `frontRows + rearRows + 5` cells.
 */
internal class TorsoWithRearView(
    private val front: LocationDiagram.Location,
    private val frontWidth: Int,
    private val frontRows: Int,
    private val rear: LocationDiagram.Location,
    private val rearWidth: Int,
    private val rearRows: Int,
    private val side: BodySide,
) : Widget {

    override fun render(canvas: Canvas) {
        val frontStyle = front.style
        canvas.setString(0, 0, topEdge(frontWidth), frontStyle)
        PipRowsView(front, frontWidth, frontRows).render(canvas.region(Rect(0, 1, frontWidth + 2, frontRows)))

        val transitionY = frontRows + 1
        when (side) {
            BodySide.LEFT -> {
                canvas.setString(1, transitionY, "╲", frontStyle)
                canvas.setString(frontWidth + 1, transitionY, "│", frontStyle)
            }
            BodySide.CENTER -> {
                canvas.setString(0, transitionY, "│", frontStyle)
                canvas.setString(frontWidth + 1, transitionY, "│", frontStyle)
            }
            BodySide.RIGHT -> {
                canvas.setString(0, transitionY, "│", frontStyle)
                canvas.setString(frontWidth, transitionY, "╱", frontStyle)
            }
        }

        val rearStyle = rear.style
        val rearX = if (side == BodySide.LEFT) frontWidth - rearWidth else 0
        val separatorY = transitionY + 1
        canvas.setString(rearX, separatorY, "│" + separator() + "│", rearStyle)
        PipRowsView(rear, rearWidth, rearRows).render(canvas.region(Rect(rearX, separatorY + 1, rearWidth + 2, rearRows)))
        val bottomY = separatorY + rearRows + 1
        canvas.setString(rearX, bottomY, bottomEdge(rearWidth), rearStyle)

        val caption = "${rear.remaining}/${rear.max}"
        val captionX = when (side) {
            BodySide.LEFT -> rearX + 1
            BodySide.CENTER -> rearX + (rearWidth + 2 - CellWidth.of(caption)) / 2
            BodySide.RIGHT -> rearX + rearWidth + 1 - CellWidth.of(caption)
        }
        canvas.setString(captionX, bottomY + 1, caption, rearStyle)
    }

    private fun separator(): String {
        if (side != BodySide.CENTER) return "╌".repeat(rearWidth)
        val fill = rearWidth - CellWidth.of(REAR_LABEL)
        require(fill >= 0) { "center rear torso width must fit $REAR_LABEL" }
        val leftFill = fill / 2
        return "╌".repeat(leftFill) + REAR_LABEL + "╌".repeat(fill - leftFill)
    }

    private companion object {
        private const val REAR_LABEL = "REAR"
    }
}
