package battletech.tactical.model

public data class GameState(
    public val units: List<battletech.tactical.action.Unit>,
    public val map: GameMap,
) {
    public fun unitAt(position: HexCoordinates): battletech.tactical.action.Unit? = units.find { it.position == position }
    public fun unitById(id: battletech.tactical.action.UnitId): battletech.tactical.action.Unit? = units.find { it.id == id }
}
