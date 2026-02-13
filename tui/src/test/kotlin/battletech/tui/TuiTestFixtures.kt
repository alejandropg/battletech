package battletech.tui

import battletech.tactical.action.Unit
import battletech.tactical.action.UnitId
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain

internal fun aHex(
    col: Int = 0,
    row: Int = 0,
    terrain: Terrain = Terrain.CLEAR,
    elevation: Int = 0,
): Hex = Hex(
    coordinates = HexCoordinates(col, row),
    terrain = terrain,
    elevation = elevation,
)

internal fun aGameMap(
    cols: Int = 3,
    rows: Int = 3,
    terrain: Terrain = Terrain.CLEAR,
): GameMap {
    val hexes = mutableMapOf<HexCoordinates, Hex>()
    for (col in 0 until cols) {
        for (row in 0 until rows) {
            val coords = HexCoordinates(col, row)
            hexes[coords] = Hex(coords, terrain)
        }
    }
    return GameMap(hexes)
}

internal fun aUnit(
    id: String = "unit-1",
    name: String = "Atlas",
    position: HexCoordinates = HexCoordinates(0, 0),
): Unit = Unit(
    id = UnitId(id),
    name = name,
    gunnerySkill = 4,
    pilotingSkill = 5,
    weapons = emptyList(),
    position = position,
)

internal fun aGameState(
    units: List<Unit> = emptyList(),
    map: GameMap = aGameMap(),
): GameState = GameState(
    units = units,
    map = map,
)
