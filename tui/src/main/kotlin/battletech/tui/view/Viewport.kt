package battletech.tui.view

import battletech.tui.hex.HexGeometry

public data class Viewport(
    val scrollCol: Int,
    val scrollRow: Int,
    val widthChars: Int,
    val heightChars: Int,
) {
    public fun visibleHexRange(): Pair<IntRange, IntRange> {
        val visibleCols = widthChars / HexGeometry.COL_STRIDE
        val visibleRows = heightChars / HexGeometry.ROW_STRIDE
        val colRange = scrollCol until (scrollCol + visibleCols)
        val rowRange = scrollRow until (scrollRow + visibleRows)
        return colRange to rowRange
    }
}
