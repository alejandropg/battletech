package battletech.tui.game

import battletech.tactical.action.UnitId
import battletech.tactical.model.GameState
import battletech.tactical.movement.ReachableHex

public object MovementResolver {

    public fun apply(gameState: GameState, unitId: UnitId, destination: ReachableHex): GameState {
        val updatedUnits = gameState.units.map { unit ->
            if (unit.id == unitId) {
                unit.copy(position = destination.position, facing = destination.facing)
            } else {
                unit
            }
        }
        return gameState.copy(units = updatedUnits)
    }
}
