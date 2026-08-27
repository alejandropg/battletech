package battletech.tactical.model

import kotlinx.serialization.Serializable

/**
 * [name] identifies which map this is — the built-in map name, or the filesystem path it was
 * loaded from — so a client that receives a [GameMap] over the wire can say which board it is
 * playing on. Defaults to `""` for the many test call sites that build a [GameMap] directly
 * without going through [battletech.tactical.model.map.resolveMap].
 */
@Serializable
public data class GameMap(
    public val hexes: Map<HexCoordinates, Hex>,
    public val name: String = "",
)
