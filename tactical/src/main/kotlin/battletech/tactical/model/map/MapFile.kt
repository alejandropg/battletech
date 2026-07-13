package battletech.tactical.model.map

import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import kotlinx.serialization.Serializable

/**
 * On-disk shape of a compact map file: a board size plus only the non-default hexes.
 * Every hex not listed in [hexes] is generated as [Terrain.CLEAR] at elevation/depth 0.
 */
@Serializable
public data class MapFile(
    public val width: Int,
    public val height: Int,
    public val hexes: List<HexSpec> = emptyList(),
) {

    /** Expands this compact description into a full [GameMap], validating bounds. */
    public fun toGameMap(): GameMap {
        if (width <= 0) throw MapLoadException("Map width must be positive, was $width")
        if (height <= 0) throw MapLoadException("Map height must be positive, was $height")

        val grid = mutableMapOf<HexCoordinates, Hex>()
        for (col in 0 until width) {
            for (row in 0 until height) {
                val coords = HexCoordinates(col, row)
                grid[coords] = Hex(coords)
            }
        }

        for (spec in hexes) {
            if (spec.col !in 0 until width || spec.row !in 0 until height) {
                throw MapLoadException(
                    "Hex (${spec.col}, ${spec.row}) is out of bounds for a ${width}x$height map"
                )
            }
            val coords = HexCoordinates(spec.col, spec.row)
            grid[coords] = Hex(coords, spec.terrain, spec.elevation, spec.depth)
        }

        return GameMap(grid)
    }
}

/** A single non-default hex override in a [MapFile]. */
@Serializable
public data class HexSpec(
    public val col: Int,
    public val row: Int,
    public val terrain: Terrain = Terrain.CLEAR,
    public val elevation: Int = 0,
    public val depth: Int = 0,
)
