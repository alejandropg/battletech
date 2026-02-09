package battletech.tactical.model

public data class Hex(
    val coordinates: HexCoordinates,
    val terrain: Terrain = Terrain.CLEAR,
    val elevation: Int = 0,
)
