package battletech.tactical.model

import battletech.tactical.action.UnitId

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

public fun GameState.applyHeatDissipation(): GameState {
    val updatedUnits = units.map { unit ->
        val newHeat = maxOf(0, unit.currentHeat - unit.heatSinkCapacity)
        unit.copy(currentHeat = newHeat)
    }
    return copy(units = updatedUnits)
}
