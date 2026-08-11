package battletech.tactical.model.map

import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain

/** Registry of built-in maps, addressable by id (e.g. `"default"`). */
public object MapCatalog {

    private val builtins: Map<String, () -> GameMap> = mapOf("default" to ::defaultMap)

    /**
     * Ids of all built-in maps. No production caller yet — kept deliberately: `--map <name>`
     * already accepts these ids, so anything that has to *offer* the choice (a `--list-maps`
     * flag, a map picker) needs exactly this, and it cannot be derived from [get].
     */
    public fun ids(): Set<String> = builtins.keys

    /** Looks up a built-in map by [id], or `null` if no built-in map has that id. */
    public operator fun get(id: String): GameMap? = builtins[id]?.invoke()

    /**
     * The 10x10 board used before map loading was introduced. No longer byte-for-byte the
     * original layout: elevation, water depth, and a rough-terrain patch were added to give the
     * TUI's terrain color roles something to exercise. All original terrain families, and every
     * sample spawn cell, are unchanged. Kept in sync with `map/default.json` — see
     * `MapSourceTest`.
     */
    public fun defaultMap(): GameMap {
        val hexes = mutableMapOf<HexCoordinates, Hex>()
        for (col in 0..9) {
            for (row in 0..9) {
                val coords = HexCoordinates(col, row)
                val terrain = when {
                    col == 3 && row in 2..5 -> Terrain.LIGHT_WOODS
                    col == 4 && row in 3..4 -> Terrain.HEAVY_WOODS
                    col == 6 && row in 1..3 -> Terrain.WATER
                    col == 1 && row in 6..7 -> Terrain.ROUGH
                    col == 2 && row in 6..8 -> Terrain.ROUGH
                    else -> Terrain.CLEAR
                }
                val elevation = when {
                    col == 5 && row == 1 -> 3
                    col == 5 && row == 2 -> 2
                    col == 5 && row in 3..4 -> 1
                    col == 3 && row == 2 -> 1
                    col == 4 && row == 3 -> 2
                    col == 1 && row == 7 -> 1
                    col == 2 && row == 6 -> 1
                    col == 2 && row == 7 -> 2
                    else -> 0
                }
                val depth = when {
                    col == 6 && row == 1 -> 1
                    col == 6 && row == 2 -> 2
                    col == 6 && row == 3 -> 1
                    else -> 0
                }
                hexes[coords] = Hex(coords, terrain, elevation, depth)
            }
        }
        return GameMap(hexes)
    }
}
