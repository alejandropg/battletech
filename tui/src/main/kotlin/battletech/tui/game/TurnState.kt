package battletech.tui.game

import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.MovementImpulse
import battletech.tactical.action.PlayerId
import battletech.tactical.action.Unit
import battletech.tactical.action.UnitId
import battletech.tactical.model.GameState

public data class TurnState(
    val initiativeResult: InitiativeResult,
    val movementOrder: List<MovementImpulse>,
    val currentImpulseIndex: Int = 0,
    val movedUnitIds: Set<UnitId> = emptySet(),
    val unitsMovedInCurrentImpulse: Int = 0,
) {
    val currentImpulse: MovementImpulse get() = movementOrder[currentImpulseIndex]
    val activePlayer: PlayerId get() = currentImpulse.player
    val remainingInImpulse: Int get() = currentImpulse.unitCount - unitsMovedInCurrentImpulse
    val allImpulsesComplete: Boolean get() = currentImpulseIndex >= movementOrder.size
}

public fun advanceAfterUnitMoved(turnState: TurnState, unitId: UnitId): TurnState {
    val updated = turnState.copy(
        movedUnitIds = turnState.movedUnitIds + unitId,
        unitsMovedInCurrentImpulse = turnState.unitsMovedInCurrentImpulse + 1,
    )
    return if (updated.remainingInImpulse == 0) {
        updated.copy(
            currentImpulseIndex = updated.currentImpulseIndex + 1,
            unitsMovedInCurrentImpulse = 0,
        )
    } else {
        updated
    }
}

public fun selectableUnits(gameState: GameState, turnState: TurnState): List<Unit> =
    gameState.unitsOf(turnState.activePlayer).filter { it.id !in turnState.movedUnitIds }

public fun validateUnitSelection(unit: Unit, turnState: TurnState): UnitSelectionResult = when {
    unit.owner != turnState.activePlayer -> UnitSelectionResult.NOT_YOUR_UNIT
    unit.id in turnState.movedUnitIds -> UnitSelectionResult.ALREADY_MOVED
    else -> UnitSelectionResult.VALID
}
