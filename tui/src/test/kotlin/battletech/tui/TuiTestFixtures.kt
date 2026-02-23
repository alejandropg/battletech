package battletech.tui

import battletech.tactical.action.Unit
import battletech.tactical.action.UnitId
import battletech.tactical.model.ArmorLayout
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
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
    facing: HexDirection = HexDirection.N,
    walkingMP: Int = 0,
    runningMP: Int = 0,
    jumpMP: Int = 0,
    armor: ArmorLayout? = null,
): Unit = Unit(
    id = UnitId(id),
    name = name,
    gunnerySkill = 4,
    pilotingSkill = 5,
    weapons = emptyList(),
    position = position,
    facing = facing,
    walkingMP = walkingMP,
    runningMP = runningMP,
    jumpMP = jumpMP,
    armor = armor,
)

internal fun anArmorLayout(
    head: Int = 9,
    centerTorso: Int = 47, centerTorsoRear: Int = 14,
    leftTorso: Int = 32, leftTorsoRear: Int = 10,
    rightTorso: Int = 32, rightTorsoRear: Int = 10,
    leftArm: Int = 34, rightArm: Int = 34,
    leftLeg: Int = 41, rightLeg: Int = 41,
): ArmorLayout = ArmorLayout(
    head = head,
    centerTorso = centerTorso, centerTorsoRear = centerTorsoRear,
    leftTorso = leftTorso, leftTorsoRear = leftTorsoRear,
    rightTorso = rightTorso, rightTorsoRear = rightTorsoRear,
    leftArm = leftArm, rightArm = rightArm,
    leftLeg = leftLeg, rightLeg = rightLeg,
)

internal fun aGameState(
    units: List<Unit> = emptyList(),
    map: GameMap = aGameMap(),
): GameState = GameState(
    units = units,
    map = map,
)
