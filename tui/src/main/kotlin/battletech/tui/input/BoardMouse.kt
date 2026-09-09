package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tui.hex.HexLayout
import com.github.ajalt.mordant.input.MouseEvent
import tenter.screen.Point

internal object BoardMouse {
    fun mapContentToHex(event: MouseEvent, contentPoint: Point?): HexCoordinates? {
        if (!event.left) return null
        val point = contentPoint ?: return null
        val x = point.x - battletech.tui.view.BoardView.MAP_ORIGIN_X
        val y = point.y - battletech.tui.view.BoardView.MAP_ORIGIN_Y
        if (x < 0 || y < 0) return null
        return HexLayout.screenToHex(x, y, scrollX = 0, scrollY = 0)
    }
}
