package battletech.tui

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.Terrain
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.InternalStructureLayout
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapon

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

internal fun anInternalStructureLayout(
    head: Int = 3,
    centerTorso: Int = 31,
    leftTorso: Int = 21,
    rightTorso: Int = 21,
    leftArm: Int = 17,
    rightArm: Int = 17,
    leftLeg: Int = 21,
    rightLeg: Int = 21,
): InternalStructureLayout = InternalStructureLayout(
    head = head,
    centerTorso = centerTorso,
    leftTorso = leftTorso,
    rightTorso = rightTorso,
    leftArm = leftArm,
    rightArm = rightArm,
    leftLeg = leftLeg,
    rightLeg = rightLeg,
)

internal fun aUnit(
    id: String = "unit-1",
    owner: PlayerId = PlayerId.PLAYER_1,
    name: String = "Atlas",
    position: HexCoordinates = HexCoordinates(0, 0),
    facing: HexDirection = HexDirection.N,
    walkingMP: Int = 0,
    runningMP: Int = 0,
    jumpMP: Int = 0,
    weapons: List<Weapon> = emptyList(),
    armor: ArmorLayout = anArmorLayout(),
    heatSink: HeatSink = HeatSink(HeatSinkType.STS, 10),
    internalStructure: InternalStructureLayout = anInternalStructureLayout(),
): CombatUnit = CombatUnit(
    id = UnitId(id),
    owner = owner,
    name = name,
    tonnage = 50,
    gunnerySkill = 4,
    pilotingSkill = 5,
    weapons = weapons,
    position = position,
    facing = facing,
    walkingMP = walkingMP,
    runningMP = runningMP,
    jumpMP = jumpMP,
    armor = armor,
    heatSink = heatSink,
    internalStructure = internalStructure,
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
    units: List<CombatUnit> = emptyList(),
    map: GameMap = aGameMap(),
): GameState = GameState(
    units = units,
    map = map,
)
