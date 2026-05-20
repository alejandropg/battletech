package battletech.tactical.attack

import battletech.tactical.model.UnitId
import battletech.tactical.model.GameState
import battletech.tactical.model.HexDirection

public fun GameState.applyTorsoFacings(facings: Map<UnitId, HexDirection>): GameState {
    if (facings.isEmpty()) return this
    val updatedUnits = units.map { unit ->
        val torso = facings[unit.id]
        if (torso != null) unit.copy(torsoFacing = torso) else unit
    }
    return copy(units = updatedUnits)
}

public fun GameState.resetTorsoFacings(): GameState {
    val updatedUnits = units.map { unit ->
        if (unit.torsoFacing != unit.facing) unit.copy(torsoFacing = unit.facing) else unit
    }
    return copy(units = updatedUnits)
}
