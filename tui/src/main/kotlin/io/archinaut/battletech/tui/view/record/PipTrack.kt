package io.archinaut.battletech.tui.view.record

import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.text.CellWidth

/**
 * A "used-of-capacity" pip track: used pips drawn with [filled], the rest with [empty], wrapping to
 * a new row every [perRow] pips. The shared layout behind every discrete-dot render of a bounded
 * resource on the record sheet — those differ only in glyphs, capacity, and pips per row.
 *
 * It only places pips; the caller paints each one wherever its own surface is, so the same track
 * serves a text card and a painted diagram alike.
 */
internal class PipTrack(
    private val filled: String,
    private val empty: String,
    private val perRow: Int,
    private val spacing: Int = 1,
) {
    init {
        require(perRow > 0) { "perRow must be positive, was $perRow" }
        require(spacing >= 0) { "spacing must not be negative, was $spacing" }
    }

    /** The number of rows [capacity] pips occupy. */
    fun rows(capacity: Int): Int = ((capacity - 1).coerceAtLeast(0) / perRow) + 1

    /**
     * Calls [paint] for each of [capacity] pips at its (column, row) offset from the track's
     * top-left: first [used] (coerced into `0..capacity`) filled pips, then the empty ones.
     */
    fun forEachPip(
        used: Int,
        capacity: Int,
        usedStyle: Style,
        emptyStyle: Style,
        paint: (column: Int, row: Int, glyph: String, style: Style) -> Unit,
    ) {
        val filledCount = used.coerceIn(0, capacity)
        val stride = maxOf(CellWidth.of(filled), CellWidth.of(empty)) + spacing
        for (i in 0 until capacity) {
            val column = (i % perRow) * stride
            val row = i / perRow
            if (i < filledCount) paint(column, row, filled, usedStyle) else paint(column, row, empty, emptyStyle)
        }
    }
}
