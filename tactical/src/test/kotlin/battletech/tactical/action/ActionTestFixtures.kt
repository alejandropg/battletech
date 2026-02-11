package battletech.tactical.action

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Weapon

internal fun mediumLaser(): Weapon = Weapon(
    name = "Medium Laser",
    damage = 5,
    heat = 3,
    shortRange = 3,
    mediumRange = 6,
    longRange = 9,
)

internal fun srm4(): Weapon = Weapon(
    name = "SRM-4",
    damage = 8,
    heat = 3,
    shortRange = 3,
    mediumRange = 6,
    longRange = 9,
    ammo = 25,
)

internal fun ac20(): Weapon = Weapon(
    name = "AC/20",
    damage = 20,
    heat = 7,
    minimumRange = 3,
    shortRange = 3,
    mediumRange = 6,
    longRange = 9,
    ammo = 5,
)

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

internal fun aUnit(
    id: String = "unit-1",
    name: String = "Test Mech",
    gunnerySkill: Int = 4,
    pilotingSkill: Int = 5,
    weapons: List<Weapon> = listOf(mediumLaser()),
    position: HexCoordinates = HexCoordinates(0, 0),
    walkingMP: Int = 4,
    runningMP: Int = 6,
    jumpMP: Int = 0,
    currentHeat: Int = 0,
    heatSinkCapacity: Int = 10,
): Unit = Unit(
    id = UnitId(id),
    name = name,
    gunnerySkill = gunnerySkill,
    pilotingSkill = pilotingSkill,
    weapons = weapons,
    position = position,
    walkingMP = walkingMP,
    runningMP = runningMP,
    jumpMP = jumpMP,
    currentHeat = currentHeat,
    heatSinkCapacity = heatSinkCapacity,
)

internal fun aGameState(
    units: List<Unit> = emptyList(),
    hexes: Map<HexCoordinates, Hex> = emptyMap(),
): GameState = GameState(
    units = units,
    map = GameMap(hexes),
)

