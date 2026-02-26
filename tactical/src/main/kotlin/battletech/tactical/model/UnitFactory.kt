package battletech.tactical.model

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId

public fun MechModel.createUnit(
    id: UnitId,
    owner: PlayerId,
    gunnerySkill: Int = 4,
    pilotingSkill: Int = 5,
    position: HexCoordinates,
    facing: HexDirection = HexDirection.N,
): CombatUnit = CombatUnit(
    id = id,
    owner = owner,
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
