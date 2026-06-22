package battletech.tactical.session

import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.InternalStructureLayout
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.WeaponModels

internal fun aMech(id: String, owner: PlayerId, position: HexCoordinates): CombatUnit = CombatUnit(
    id = UnitId(id),
    owner = owner,
    name = id,
    tonnage = 50,
    gunnerySkill = 4,
    pilotingSkill = 5,
    weapons = listOf(Weapon(model = WeaponModels.mediumLaser)),
    position = position,
    facing = HexDirection.N,
    torsoFacing = HexDirection.N,
    walkingMP = 4,
    runningMP = 6,
    jumpMP = 0,
    currentHeat = 0,
    heatSink = HeatSink(HeatSinkType.STS, 10),
    armor = ArmorLayout(
        head = 9,
        centerTorso = 30, centerTorsoRear = 10,
        leftTorso = 25, leftTorsoRear = 8,
        rightTorso = 25, rightTorsoRear = 8,
        leftArm = 20, rightArm = 20,
        leftLeg = 25, rightLeg = 25,
    ),
    internalStructure = InternalStructureLayout(
        head = 3,
        centerTorso = 31,
        leftTorso = 21,
        rightTorso = 21,
        leftArm = 17,
        rightArm = 17,
        leftLeg = 21,
        rightLeg = 21,
    ),
)

/** Builds a map covering every hex occupied by or adjacent to the given units. */
internal fun hexesFor(units: List<CombatUnit>): Map<HexCoordinates, Hex> {
    val coords = units.flatMap { u ->
        listOf(u.position) + HexDirection.entries.map { u.position.neighbor(it) }
    }
    return coords.distinct().associateWith { Hex(it) }
}

internal fun anInitiative(): Initiative = Initiative(
    rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
    loser = PlayerId.PLAYER_1,
    winner = PlayerId.PLAYER_2,
)

internal fun aMovementTurn(
    movementOrder: List<Impulse> = listOf(Impulse(PlayerId.PLAYER_1, 1), Impulse(PlayerId.PLAYER_2, 1)),
    currentImpulseIndex: Int = 0,
    movedUnitIds: Set<UnitId> = emptySet(),
    movedInCurrentImpulse: Int = 0,
): TurnState = TurnState(
    initiative = anInitiative(),
    movement = MovementProgress(
        sequence = ImpulseSequence(movementOrder, currentImpulseIndex),
        movedUnitIds = movedUnitIds,
        movedInCurrentImpulse = movedInCurrentImpulse,
    ),
)

internal fun anAttackTurn(
    attackOrder: List<Impulse> = listOf(Impulse(PlayerId.PLAYER_1, 1), Impulse(PlayerId.PLAYER_2, 1)),
): TurnState = TurnState(
    initiative = anInitiative(),
    attack = AttackProgress(sequence = ImpulseSequence(attackOrder)),
)
