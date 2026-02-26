package battletech.tactical.action

import battletech.tactical.model.ArmorLayout
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Weapon
import battletech.tactical.model.Weapons

internal fun mediumLaser(): Weapon = Weapons.mediumLaser()

internal fun srm4(): Weapon = Weapon(
    name = "SRM-4",
    damage = 8,
    heat = 3,
    shortRange = 3,
    mediumRange = 6,
    longRange = 9,
    ammo = 25,
)

internal fun ac20(): Weapon = Weapons.ac20()

internal fun aWeapon(
    name: String = "Test Weapon",
    damage: Int = 5,
    heat: Int = 3,
    minimumRange: Int = 0,
    shortRange: Int = 3,
    mediumRange: Int = 6,
    longRange: Int = 9,
    ammo: Int? = null,
    destroyed: Boolean = false,
): Weapon = Weapon(
    name = name,
    damage = damage,
    heat = heat,
    minimumRange = minimumRange,
    shortRange = shortRange,
    mediumRange = mediumRange,
    longRange = longRange,
    ammo = ammo,
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

internal fun aUnit(
    id: String = "unit-1",
    owner: PlayerId = PlayerId.PLAYER_1,
    name: String = "Test Mech",
    gunnerySkill: Int = 4,
    pilotingSkill: Int = 5,
    weapons: List<Weapon> = listOf(mediumLaser()),
    position: HexCoordinates = HexCoordinates(0, 0),
    facing: HexDirection = HexDirection.N,
    walkingMP: Int = 4,
    runningMP: Int = 6,
    jumpMP: Int = 0,
    currentHeat: Int = 0,
    heatSinkCapacity: Int = 10,
    armor: ArmorLayout = anArmorLayout(),
): Unit = Unit(
    id = UnitId(id),
    owner = owner,
    name = name,
    gunnerySkill = gunnerySkill,
    pilotingSkill = pilotingSkill,
    weapons = weapons,
    position = position,
    facing = facing,
    walkingMP = walkingMP,
    runningMP = runningMP,
    jumpMP = jumpMP,
    currentHeat = currentHeat,
    heatSinkCapacity = heatSinkCapacity,
    armor = armor,
)

internal fun aGameState(
    units: List<Unit> = emptyList(),
    hexes: Map<HexCoordinates, Hex> = emptyMap(),
): GameState = GameState(
    units = units,
    map = GameMap(hexes),
)

