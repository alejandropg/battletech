package battletech.tactical.query

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.CriticalLayout
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.InternalStructureLayout
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.WeaponKind
import battletech.tactical.unit.WeaponModel
import battletech.tactical.unit.WeaponModels
import battletech.tactical.unit.empty

internal fun mediumLaser(): Weapon = Weapon(model = WeaponModels.mediumLaser)

internal fun ac20(): Weapon = Weapon(model = WeaponModels.ac20)

internal fun aWeapon(
    name: String = "Test Weapon",
    damage: Int = 5,
    heat: Int = 3,
    minimumRange: Int = 0,
    shortRange: Int = 3,
    mediumRange: Int = 6,
    longRange: Int = 9,
    kind: WeaponKind = WeaponKind.Energy,
    destroyed: Boolean = false,
): Weapon = Weapon(
    model = WeaponModel(
        name = name,
        damage = damage,
        heat = heat,
        minimumRange = minimumRange,
        shortRange = shortRange,
        mediumRange = mediumRange,
        longRange = longRange,
        kind = kind,
    ),
    destroyed = destroyed,
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
    name: String = "Test Mech",
    tonnage: Int = 50,
    gunnerySkill: Int = 4,
    pilotingSkill: Int = 5,
    weapons: List<Weapon> = listOf(mediumLaser()),
    position: HexCoordinates = HexCoordinates(0, 0),
    facing: HexDirection = HexDirection.N,
    walkingMP: Int = 4,
    runningMP: Int = 6,
    jumpMP: Int = 0,
    currentHeat: Int = 0,
    heatSink: HeatSink = HeatSink(HeatSinkType.STS, 10),
    armor: ArmorLayout = anArmorLayout(),
    internalStructure: InternalStructureLayout = anInternalStructureLayout(),
    criticalLayout: CriticalLayout = CriticalLayout.empty(),
    isDestroyed: Boolean = false,
    pilotHits: Int = 0,
    isPilotConscious: Boolean = true,
): CombatUnit = CombatUnit(
    id = UnitId(id),
    owner = owner,
    name = name,
    tonnage = tonnage,
    gunnerySkill = gunnerySkill,
    pilotingSkill = pilotingSkill,
    weapons = weapons,
    position = position,
    facing = facing,
    walkingMP = walkingMP,
    runningMP = runningMP,
    jumpMP = jumpMP,
    currentHeat = currentHeat,
    heatSink = heatSink,
    armor = armor,
    internalStructure = internalStructure,
    criticalLayout = criticalLayout,
    isDestroyed = isDestroyed,
    pilotHits = pilotHits,
    isPilotConscious = isPilotConscious,
)

internal fun aGameState(
    units: List<CombatUnit> = emptyList(),
    hexes: Map<HexCoordinates, Hex> = emptyMap(),
): GameState = GameState(
    units = units,
    map = GameMap(hexes),
)

