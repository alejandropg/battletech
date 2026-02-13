package battletech.tui.input

import battletech.tactical.action.UnitId
import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public data class CursorState(
    val position: HexCoordinates,
    val selectedUnitId: UnitId? = null,
) {
    public fun moveCursor(direction: HexDirection, map: GameMap): CursorState {
        val neighbor = position.neighbor(direction)
        return if (neighbor in map.hexes) {
            copy(position = neighbor)
        } else {
            this
        }
    }
}
