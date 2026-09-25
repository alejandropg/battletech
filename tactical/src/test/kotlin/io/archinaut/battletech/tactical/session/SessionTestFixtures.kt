package io.archinaut.battletech.tactical.session

import io.archinaut.battletech.tactical.dice.DiceRoll
import io.archinaut.battletech.tactical.model.Hex
import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tactical.model.MechLocation
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.unit.ArmorLayout
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.CriticalLayout
import io.archinaut.battletech.tactical.unit.HeatSink
import io.archinaut.battletech.tactical.unit.HeatSinkType
import io.archinaut.battletech.tactical.unit.InternalStructureLayout
import io.archinaut.battletech.tactical.unit.MechModel
import io.archinaut.battletech.tactical.unit.UnitId
import io.archinaut.battletech.tactical.unit.empty
import io.archinaut.battletech.tactical.unit.Weapon
import io.archinaut.battletech.tactical.unit.WeaponModels
import io.archinaut.battletech.tactical.unit.WeaponMountId

internal fun aMech(
    id: String,
    owner: PlayerId,
    position: HexCoordinates,
    pilotHits: Int = 0,
    isPilotConscious: Boolean = true,
): CombatUnit {
    val armor = ArmorLayout(
        head = 9,
        centerTorso = 30, centerTorsoRear = 10,
        leftTorso = 25, leftTorsoRear = 8,
        rightTorso = 25, rightTorsoRear = 8,
        leftArm = 20, rightArm = 20,
        leftLeg = 25, rightLeg = 25,
    )
    val internalStructure = InternalStructureLayout(
        head = 3,
        centerTorso = 31,
        leftTorso = 21,
        rightTorso = 21,
        leftArm = 17,
        rightArm = 17,
        leftLeg = 21,
        rightLeg = 21,
    )
    val weapons = listOf(Weapon(model = WeaponModels.mediumLaser, mountId = WeaponMountId(0), location = MechLocation.CENTER_TORSO))
    val criticalLayout = CriticalLayout.empty()
    return CombatUnit(
        model = MechModel(
            variant = "TEST",
            name = id,
            tonnage = 50,
            walkingMP = 4,
            runningMP = 6,
            heatSink = HeatSink(HeatSinkType.STS, 10),
            armor = armor,
            internalStructure = internalStructure,
            criticalLayout = criticalLayout,
            weapons = weapons,
        ),
        id = UnitId(id),
        owner = owner,
        weapons = weapons,
        position = position,
        facing = HexDirection.N,
        torsoFacing = HexDirection.N,
        pilotHits = pilotHits,
        isPilotConscious = isPilotConscious,
        armor = armor,
        internalStructure = internalStructure,
        criticalLayout = criticalLayout,
    )
}

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
