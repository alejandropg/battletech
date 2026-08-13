package tenter.view

/**
 * Pure, single-axis scroll math shared by every [Viewport] and [Bordered] instance — the one
 * place "keep this visible", "center on this", and "where does the thumb sit" are computed, so
 * no view reimplements any of them.
 */
public object ScrollGeometry {

    /**
     * The minimal offset that brings `[revealStart, revealEnd)` into `[offset, offset + viewportSize)`,
     * inset by [margin] on each side.
     *
     * Returns [offset] unchanged (clamped) when the target is already visible — this is what lets
     * a manually-panned-then-restored view "stick" rather than hunting back to a canonical
     * position. When the target is wider/taller than the viewport, aligns to [revealStart].
     */
    public fun follow(offset: Int, revealStart: Int, revealEnd: Int, viewportSize: Int, maxOffset: Int, margin: Int = 0): Int {
        if (maxOffset <= 0) return 0
        val clamped = offset.coerceIn(0, maxOffset)
        val visibleStart = clamped + margin
        val visibleEnd = clamped + viewportSize - margin
        val adjusted = when {
            revealStart < visibleStart -> revealStart - margin
            revealEnd > visibleEnd -> revealEnd - viewportSize + margin
            else -> return clamped
        }
        return adjusted.coerceIn(0, maxOffset)
    }

    /** The offset that centers `[revealStart, revealEnd)` in a [viewportSize]-wide window. */
    public fun center(revealStart: Int, revealEnd: Int, viewportSize: Int, maxOffset: Int): Int {
        if (maxOffset <= 0) return 0
        val revealSize = revealEnd - revealStart
        val centered = revealStart - (viewportSize - revealSize) / 2
        return centered.coerceIn(0, maxOffset)
    }

    /**
     * The scrollbar thumb's range within a [track]-cell-long scrollbar, or `null` if the content
     * fits entirely in the viewport (no thumb to draw). [contentLength] and [viewportLength] are
     * both in the same units as [track] and [offset] — cells along the scrolled axis, so this
     * one function serves both a vertical (heights) and a horizontal (widths) scrollbar.
     */
    public fun thumb(track: Int, contentLength: Int, viewportLength: Int, offset: Int): IntRange? {
        if (track <= 0) return null
        if (contentLength <= viewportLength) return null

        val thumbSize = maxOf(1, track * viewportLength / contentLength)
        val maxOffset = contentLength - viewportLength

        val thumbStart = if (maxOffset == 0) {
            0
        } else {
            (offset * (track - thumbSize) + maxOffset / 2) / maxOffset
        }

        return thumbStart until thumbStart + thumbSize
    }
}
