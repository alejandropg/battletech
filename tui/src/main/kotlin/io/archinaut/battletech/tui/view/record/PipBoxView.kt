package io.archinaut.battletech.tui.view.record

import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.widget.Widget

/** [location]'s [PipRowsView] closed by a rounded top and bottom border: `width + 2` by `rows + 2` cells. */
internal class PipBoxView(
    private val location: LocationDiagram.Location,
    private val width: Int,
    private val rows: Int,
) : Widget {

    override fun render(canvas: Canvas) {
        val edge = "─".repeat(width)
        canvas.setString(0, 0, "╭$edge╮", location.style)
        PipRowsView(location, width, rows).render(canvas.region(Rect(0, 1, width + 2, rows)))
        canvas.setString(0, rows + 1, "╰$edge╯", location.style)
    }
}
