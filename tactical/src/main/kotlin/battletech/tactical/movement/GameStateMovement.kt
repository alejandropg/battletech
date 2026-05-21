package battletech.tactical.movement

import battletech.tactical.model.GameState
import battletech.tactical.unit.UnitId

public fun GameState.moveUnit(unitId: UnitId, destination: ReachableHex): GameState {
    val updatedUnits = units.map { unit ->
        if (unit.id == unitId) {
            unit.copy(position = destination.position, facing = destination.facing, torsoFacing = destination.facing)
        } else {
            unit
        }
    }
    return copy(units = updatedUnits)
}
