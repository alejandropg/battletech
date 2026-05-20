package battletech.tactical.session

import battletech.tactical.model.GameState

public fun GameState.applyHeatDissipation(): GameState {
    val updatedUnits = units.map { unit ->
        val newHeat = maxOf(0, unit.currentHeat - unit.heatSinkCapacity)
        unit.copy(currentHeat = newHeat)
    }
    return copy(units = updatedUnits)
}
