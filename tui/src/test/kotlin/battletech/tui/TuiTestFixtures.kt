package battletech.tui

import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.Terrain
import battletech.tactical.session.AttackProgress
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.MovementProgress
import battletech.tactical.session.TurnState
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.CriticalLayout
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.InternalStructureLayout
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.Weapons
import battletech.tactical.unit.empty
import battletech.tui.game.AppState
import battletech.tui.game.phase.Phase

internal fun mediumLaser(): Weapon = Weapons.mediumLaser()

internal fun srm6(): Weapon = Weapon(
    name = "SRM 6", damage = 12, heat = 4,
    shortRange = 3, mediumRange = 6, longRange = 9,
)

internal fun aTurnState(
    movementOrder: List<Impulse> = listOf(Impulse(PlayerId.PLAYER_1, 1)),
    currentImpulseIndex: Int = 0,
    movedUnitIds: Set<UnitId> = emptySet(),
    attackOrder: List<Impulse> = listOf(Impulse(PlayerId.PLAYER_1, 1), Impulse(PlayerId.PLAYER_2, 1)),
    currentAttackImpulseIndex: Int = 0,
): TurnState = TurnState(
    initiative = Initiative(
        rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
        loser = PlayerId.PLAYER_1,
        winner = PlayerId.PLAYER_2,
    ),
    movement = MovementProgress(
        sequence = ImpulseSequence(movementOrder, currentImpulseIndex),
        movedUnitIds = movedUnitIds,
    ),
    attack = AttackProgress(
        sequence = ImpulseSequence(attackOrder, currentAttackImpulseIndex),
    ),
)

internal fun anAppState(
    phase: Phase,
    cursor: HexCoordinates = HexCoordinates(0, 0),
    gameState: GameState = aGameState(),
    turnState: TurnState = aTurnState(),
): AppState = AppState(gameState, turnState, phase, cursor)

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
    currentHeat: Int = 0,
    weapons: List<Weapon> = emptyList(),
    armor: ArmorLayout = anArmorLayout(),
    heatSink: HeatSink = HeatSink(HeatSinkType.STS, 10),
    internalStructure: InternalStructureLayout = anInternalStructureLayout(),
    criticalLayout: CriticalLayout = CriticalLayout.empty(),
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
    currentHeat = currentHeat,
    armor = armor,
    heatSink = heatSink,
    internalStructure = internalStructure,
    criticalLayout = criticalLayout,
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
