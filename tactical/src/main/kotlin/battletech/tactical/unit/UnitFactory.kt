package battletech.tactical.unit

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId

public fun MechModel.createUnit(
    id: UnitId,
    owner: PlayerId,
    gunnerySkill: Int = 4,
    pilotingSkill: Int = 5,
    position: HexCoordinates,
    facing: HexDirection = HexDirection.N,
): CombatUnit = CombatUnit(
    model = this,
    id = id,
    owner = owner,
    gunnerySkill = gunnerySkill,
    pilotingSkill = pilotingSkill,
    position = position,
    facing = facing,
)
