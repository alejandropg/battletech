package tenter.widget

import tenter.screen.Cell
import tenter.text.CellWidth
import tenter.view.TextCursor

/**
 * A record-sheet style "used-of-capacity" pip track: [used] pips drawn with [filled], the
 * remaining `capacity - used` drawn with [empty], wrapping to a new row every [perRow] pips.
 * This is the shared primitive behind every discrete-dot render (pilot hits, crit slots,
 * armor/structure diagrams) — those differ only in glyphs, capacity, and how many pips fit
 * per row, not in the wrapping/coloring logic itself.
 *
 * [draw] paints at an explicit `(column, row)` and never moves [TextCursor]'s own cursor — the
 * same convention as [TextCursor.write]/[TextCursor.writeAt] — so callers can place several
 * tracks side by side on the same row band (e.g. one per body location) as well as stack them
 * one after another; either way the caller decides how far to advance from the returned row
 * count.
 */
public class PipTrack(
    private val filled: String,
    private val empty: String,
    private val perRow: Int,
    private val spacing: Int = 1,
) {
    init {
        require(perRow > 0) { "perRow must be positive, was $perRow" }
    }

    /** The number of rows [capacity] pips will occupy, without drawing anything. */
    public fun rows(capacity: Int): Int = ((capacity - 1).coerceAtLeast(0) / perRow) + 1

    /**
     * Draws [used] (coerced into `0..capacity`) filled pips followed by the remaining empty
     * pips, [perRow] per row, starting at `([column], [row])`. Returns [rows] — the number of
     * rows occupied — so the caller can advance its own cursor.
     */
    public fun draw(
        content: TextCursor,
        column: Int,
        row: Int,
        used: Int,
        capacity: Int,
        usedStyle: Cell.Style,
        emptyStyle: Cell.Style,
    ): Int {
        val filledCount = used.coerceIn(0, capacity)
        val stride = maxOf(CellWidth.of(filled), CellWidth.of(empty)) + spacing

        for (i in 0 until capacity) {
            val r = row + i / perRow
            val col = column + (i % perRow) * stride
            if (i < filledCount) {
                content.writeAt(col, r, filled, usedStyle)
            } else {
                content.writeAt(col, r, empty, emptyStyle)
            }
        }
        return rows(capacity)
    }

    /**
     * Draws at [column] on [content]'s current row, exactly like [draw], but advances the cursor
     * past the rows used instead of leaving placement to the caller — for the common case where
     * pips are the only thing on their own band of rows.
     */
    public fun drawAdvancing(
        content: TextCursor,
        column: Int,
        used: Int,
        capacity: Int,
        usedStyle: Cell.Style,
        emptyStyle: Cell.Style,
    ) {
        val rows = draw(content, column, content.row, used, capacity, usedStyle, emptyStyle)
        repeat(rows) { content.newLine() }
    }
}
