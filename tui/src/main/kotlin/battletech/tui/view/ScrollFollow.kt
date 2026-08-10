package battletech.tui.view

/**
 * Pure, single-axis scroll-offset math shared by every [Scrolled] instance — the one
 * place "keep this visible" and "center on this" are computed, so no view reimplements either.
 */
internal object ScrollFollow {

    /**
     * The minimal offset that brings `[focusStart, focusEnd)` into `[offset, offset + viewportSize)`,
     * inset by [margin] on each side.
     *
     * Returns [offset] unchanged (clamped) when the focus is already visible — this is what lets
     * a manually-panned-then-restored view "stick" rather than hunting back to a canonical
     * position. When the focus is wider/taller than the viewport, aligns to [focusStart].
     */
    fun follow(offset: Int, focusStart: Int, focusEnd: Int, viewportSize: Int, maxOffset: Int, margin: Int = 0): Int {
        if (maxOffset <= 0) return 0
        val clamped = offset.coerceIn(0, maxOffset)
        val visibleStart = clamped + margin
        val visibleEnd = clamped + viewportSize - margin
        val adjusted = when {
            focusStart < visibleStart -> focusStart - margin
            focusEnd > visibleEnd -> focusEnd - viewportSize + margin
            else -> return clamped
        }
        return adjusted.coerceIn(0, maxOffset)
    }

    /** The offset that centers `[focusStart, focusEnd)` in a [viewportSize]-wide window. */
    fun center(focusStart: Int, focusEnd: Int, viewportSize: Int, maxOffset: Int): Int {
        if (maxOffset <= 0) return 0
        val focusSize = focusEnd - focusStart
        val centered = focusStart - (viewportSize - focusSize) / 2
        return centered.coerceIn(0, maxOffset)
    }
}
