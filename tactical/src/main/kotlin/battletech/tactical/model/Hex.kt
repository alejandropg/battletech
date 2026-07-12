package battletech.tactical.model

import kotlinx.serialization.Serializable

@Serializable
public data class Hex(
    public val coordinates: HexCoordinates,
    public val terrain: Terrain = Terrain.CLEAR,
    public val elevation: Int = 0,
    /** Water depth in levels (0 = dry). Depth 1 submerges a 'Mech's legs; depth 2+ submerges it fully. */
    public val depth: Int = 0,
)
