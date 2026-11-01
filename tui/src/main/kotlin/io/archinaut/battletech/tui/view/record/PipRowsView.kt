package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.icon.emptyCircleIcon
import io.archinaut.battletech.tui.icon.filledCircleIcon
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.widget.Widget

/**
 * [rows] rows of [location]'s pips, [width] to a row, between a side border on each edge, so it
 * is `width + 2` columns wide. A filled pip is damage taken, an empty one a point still available.
 */
internal class PipRowsView(
    private val location: LocationDiagram.Location,
    private val width: Int,
    private val rows: Int,
) : Widget {

    override fun render(canvas: Canvas) {
        val outline = location.style
        for (row in 0 until rows) {
            canvas.setString(0, row, "│", outline)
            canvas.setString(width + 1, row, "│", outline)
        }
        val damage = (location.max - location.remaining).coerceIn(0, location.max)
        PipTrack(filledCircleIcon(), emptyCircleIcon(), perRow = width, spacing = 0).forEachPip(
            used = damage,
            capacity = location.max,
            usedStyle = SheetStyles.DANGER,
            emptyStyle = outline,
        ) { column, row, glyph, style -> canvas.setString(1 + column, row, glyph, style) }
    }
}
