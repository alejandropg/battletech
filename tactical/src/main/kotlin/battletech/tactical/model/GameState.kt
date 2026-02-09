package battletech.tactical.model

public data class GameState(
    val units: List<battletech.tactical.action.Unit>,
    val map: GameMap,
)

public data class GameMap(val hexes: Map<HexCoordinates, Hex>)
