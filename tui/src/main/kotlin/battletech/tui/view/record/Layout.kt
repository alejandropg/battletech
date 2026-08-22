package battletech.tui.view.record

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.PipTrack

/**
 * Draws [view] as a self-contained band at [content]'s current row, using all of [canvas]'s
 * width — measuring the view's own height into an offscreen stream, then blitting it in and
 * advancing [content] past it. This is the composition primitive every multi-card row in the
 * record sheet needs, so cards can be stacked by height without knowing each other's row count
 * up front; [tenter.view.Columns] uses the same measure-then-place trick one level down, for the
 * cards *within* a band.
 */
internal fun drawBand(canvas: Canvas, content: TextCursor, view: View) {
    val remaining = canvas.region(0, content.row, canvas.width, canvas.height - content.row)
    if (remaining.width <= 0 || remaining.height <= 0) return
    val stream = Canvas.offscreen(remaining.width, remaining.height)
    view.draw(stream)
    val used = stream.contentHeight()
    canvas.blit(stream, 0, 0, 0, content.row, remaining.width, used)
    repeat(used) { content.newLine() }
}

/** Draws [track]'s pips at [column] on [content]'s current row, then advances past them. */
internal fun TextCursor.writePips(
    track: PipTrack,
    column: Int,
    used: Int,
    capacity: Int,
    usedStyle: Cell.Style,
    emptyStyle: Cell.Style,
) {
    val rows = track.draw(this, column, row, used, capacity, usedStyle, emptyStyle)
    repeat(rows) { newLine() }
}
