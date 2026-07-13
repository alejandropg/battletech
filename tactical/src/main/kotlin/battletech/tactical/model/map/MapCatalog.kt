package battletech.tactical.model.map

import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain

/** Registry of built-in maps, addressable by id (e.g. `"default"`). */
public object MapCatalog {

    private val builtins: Map<String, () -> GameMap> = mapOf("default" to ::defaultMap)

    /** Ids of all built-in maps. */
    public fun ids(): Set<String> = builtins.keys

    /** Looks up a built-in map by [id], or `null` if no built-in map has that id. */
    public operator fun get(id: String): GameMap? = builtins[id]?.invoke()

    /** The 10x10 board used before map loading was introduced; preserved byte-for-byte. */
    public fun defaultMap(): GameMap {
        val hexes = mutableMapOf<HexCoordinates, Hex>()
        for (col in 0..9) {
            for (row in 0..9) {
                val coords = HexCoordinates(col, row)
                val terrain = when {
                    col == 3 && row in 2..5 -> Terrain.LIGHT_WOODS
                    col == 4 && row in 3..4 -> Terrain.HEAVY_WOODS
                    col == 6 && row in 1..3 -> Terrain.WATER
                    else -> Terrain.CLEAR
                }
                val elevation = if (col == 5 && row in 2..4) 1 else 0
                hexes[coords] = Hex(coords, terrain, elevation)
            }
        }
        return GameMap(hexes)
    }
}
