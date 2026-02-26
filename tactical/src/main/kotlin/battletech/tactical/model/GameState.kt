package battletech.tactical.model

import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId

public data class GameState(
    public val units: List<battletech.tactical.action.Unit>,
    public val map: GameMap,
) {
    public fun unitAt(position: HexCoordinates): battletech.tactical.action.Unit? =
        units.find { it.position == position }

    public fun unitById(id: UnitId): battletech.tactical.action.Unit? = units.find { it.id == id }
    public fun unitsOf(player: PlayerId): List<battletech.tactical.action.Unit> = units.filter { it.owner == player }
}
