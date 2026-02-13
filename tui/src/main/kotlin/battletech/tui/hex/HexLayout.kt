package battletech.tui.hex

import battletech.tactical.model.HexCoordinates

public object HexLayout {

    public fun hexToScreen(col: Int, row: Int): Pair<Int, Int> {
        val charX = col * HexGeometry.COL_STRIDE
        val charY = row * HexGeometry.ROW_STRIDE + if (col % 2 != 0) HexGeometry.ODD_COL_ROW_OFFSET else 0
        return charX to charY
    }

    public fun screenToHex(charX: Int, charY: Int, scrollX: Int, scrollY: Int): HexCoordinates? {
        val adjustedX = charX + scrollX
        val adjustedY = charY + scrollY
        val col = adjustedX / HexGeometry.COL_STRIDE
        if (col < 0) return null
        val rowOffset = if (col % 2 != 0) HexGeometry.ODD_COL_ROW_OFFSET else 0
        val row = (adjustedY - rowOffset) / HexGeometry.ROW_STRIDE
        if (row < 0) return null
        return HexCoordinates(col, row)
    }
}
