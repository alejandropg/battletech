package battletech.tactical.model

import battletech.tactical.action.Unit
import battletech.tactical.action.UnitId

public fun MechModel.createUnit(
    id: UnitId,
    gunnerySkill: Int = 4,
    pilotingSkill: Int = 5,
    position: HexCoordinates,
    facing: HexDirection = HexDirection.N,
): Unit = Unit(
    id = id,
    name = name,
    gunnerySkill = gunnerySkill,
    pilotingSkill = pilotingSkill,
    weapons = weapons.map { it() },
    position = position,
    facing = facing,
    walkingMP = walkingMP,
    runningMP = runningMP,
    jumpMP = jumpMP,
    heatSinkCapacity = heatSinkCapacity,
    armor = armor,
)
