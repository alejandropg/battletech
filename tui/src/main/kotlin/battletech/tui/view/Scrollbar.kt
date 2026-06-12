package battletech.tui.view

internal object Scrollbar {

    fun thumb(track: Int, contentHeight: Int, viewportHeight: Int, offset: Int): IntRange? {
        if (track <= 0) return null
        if (contentHeight <= viewportHeight) return null

        val thumbSize = maxOf(1, track * viewportHeight / contentHeight)
        val maxOffset = contentHeight - viewportHeight

        val thumbStart = if (maxOffset == 0) {
            0
        } else {
            (offset * (track - thumbSize) + maxOffset / 2) / maxOffset
        }

        return thumbStart until thumbStart + thumbSize
    }
}
