package battletech.tui.game

import battletech.tactical.action.UnitId
import battletech.tactical.model.GameState
import battletech.tactical.model.HexDirection

public fun applyTorsoFacings(gameState: GameState, facings: Map<UnitId, HexDirection>): GameState {
    if (facings.isEmpty()) return gameState
    val updatedUnits = gameState.units.map { unit ->
        val torso = facings[unit.id]
        if (torso != null) unit.copy(torsoFacing = torso) else unit
    }
    return gameState.copy(units = updatedUnits)
}

public fun resetTorsoFacings(gameState: GameState): GameState {
    val updatedUnits = gameState.units.map { unit ->
        if (unit.torsoFacing != unit.facing) unit.copy(torsoFacing = unit.facing) else unit
    }
    return gameState.copy(units = updatedUnits)
}

public fun applyHeatDissipation(gameState: GameState): GameState {
    val updatedUnits = gameState.units.map { unit ->
        val newHeat = maxOf(0, unit.currentHeat - unit.heatSinkCapacity)
        unit.copy(currentHeat = newHeat)
    }
    return gameState.copy(units = updatedUnits)
}
