package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tui.hex.HexLayout
import com.github.ajalt.mordant.input.MouseEvent

internal object BoardMouse {
    fun mapMouseToHex(event: MouseEvent, boardX: Int, boardY: Int, scrollX: Int = 0, scrollY: Int = 0): HexCoordinates? {
        if (!event.left) return null
        val x = event.x - boardX
        val y = event.y - boardY
        if (x < 0 || y < 0) return null
        return HexLayout.screenToHex(x, y, scrollX, scrollY)
    }
}
