package battletech.tui.game

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.MovementImpulse
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.model.GameState

public data class TurnState(
    val initiativeResult: InitiativeResult,
    val movementOrder: List<MovementImpulse>,
    val currentImpulseIndex: Int = 0,
    val movedUnitIds: Set<UnitId> = emptySet(),
    val unitsMovedInCurrentImpulse: Int = 0,
    val attackOrder: List<MovementImpulse> = emptyList(),
    val attackedUnitIds: Set<UnitId> = emptySet(),
    val currentAttackImpulseIndex: Int = 0,
    val unitsAttackedInCurrentImpulse: Int = 0,
) {
    val currentImpulse: MovementImpulse get() = movementOrder[currentImpulseIndex]
    val activePlayer: PlayerId get() = currentImpulse.player
    val remainingInImpulse: Int get() = currentImpulse.unitCount - unitsMovedInCurrentImpulse
    val allImpulsesComplete: Boolean get() = currentImpulseIndex >= movementOrder.size

    val currentAttackImpulse: MovementImpulse get() = attackOrder[currentAttackImpulseIndex]
    val activeAttackPlayer: PlayerId get() = currentAttackImpulse.player
    val remainingInAttackImpulse: Int get() = currentAttackImpulse.unitCount - unitsAttackedInCurrentImpulse
    val allAttackImpulsesComplete: Boolean get() = currentAttackImpulseIndex >= attackOrder.size
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

public fun advanceAfterUnitAttacked(turnState: TurnState, unitId: UnitId): TurnState {
    val updated = turnState.copy(
        attackedUnitIds = turnState.attackedUnitIds + unitId,
        unitsAttackedInCurrentImpulse = turnState.unitsAttackedInCurrentImpulse + 1,
    )
    return if (updated.remainingInAttackImpulse == 0) {
        updated.copy(
            currentAttackImpulseIndex = updated.currentAttackImpulseIndex + 1,
            unitsAttackedInCurrentImpulse = 0,
        )
    } else {
        updated
    }
}

public fun selectableUnits(gameState: GameState, turnState: TurnState): List<CombatUnit> =
    gameState.unitsOf(turnState.activePlayer).filter { it.id !in turnState.movedUnitIds }

public fun selectableAttackUnits(gameState: GameState, turnState: TurnState): List<CombatUnit> =
    gameState.unitsOf(turnState.activeAttackPlayer).filter { it.id !in turnState.attackedUnitIds }

public fun validateUnitSelection(unit: CombatUnit, turnState: TurnState): UnitSelectionResult = when {
    unit.owner != turnState.activePlayer -> UnitSelectionResult.NOT_YOUR_UNIT
    unit.id in turnState.movedUnitIds -> UnitSelectionResult.ALREADY_MOVED
    else -> UnitSelectionResult.VALID
}

public fun validateAttackUnitSelection(unit: CombatUnit, turnState: TurnState): UnitSelectionResult = when {
    unit.owner != turnState.activeAttackPlayer -> UnitSelectionResult.NOT_YOUR_UNIT
    unit.id in turnState.attackedUnitIds -> UnitSelectionResult.ALREADY_ACTED
    else -> UnitSelectionResult.VALID
}
