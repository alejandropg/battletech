package battletech.tactical.model

import kotlinx.serialization.Serializable

@Serializable
public data class GameMap(
    public val hexes: Map<HexCoordinates, Hex>
)
