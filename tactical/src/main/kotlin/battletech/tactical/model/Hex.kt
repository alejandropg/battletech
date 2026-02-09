package battletech.tactical.model

public data class Hex(
    public val coordinates: HexCoordinates,
    public val terrain: Terrain = Terrain.CLEAR,
    public val elevation: Int = 0,
)
