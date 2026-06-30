package battletech.tactical.movement

import battletech.tactical.model.GameState
import battletech.tactical.model.mapUnits
import battletech.tactical.unit.UnitId

public fun GameState.moveUnit(unitId: UnitId, destination: ReachableHex): GameState =
    mapUnits { unit ->
        if (unit.id == unitId) {
            unit.copy(position = destination.position, facing = destination.facing, torsoFacing = destination.facing)
        } else {
            unit
        }
    }
