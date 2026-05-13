package battletech.tui.game

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.Impulse
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackDeclaration
import battletech.tactical.model.GameState

public data class TurnState(
    val initiativeResult: InitiativeResult,
    val movementSequence: ImpulseSequence,
    val movedUnitIds: Set<UnitId> = emptySet(),
    val unitsMovedInCurrentImpulse: Int = 0,
    val attackSequence: ImpulseSequence = ImpulseSequence(emptyList()),
    val attackDeclarations: List<AttackDeclaration> = emptyList(),
    val attackImpulse: ImpulseDeclarations? = null,
) {
    val currentImpulse: Impulse get() = movementSequence.current
    val activePlayer: PlayerId get() = movementSequence.activePlayer
    val remainingInImpulse: Int get() = currentImpulse.unitCount - unitsMovedInCurrentImpulse
    val allImpulsesComplete: Boolean get() = movementSequence.isComplete

    val currentAttackImpulse: Impulse get() = attackSequence.current
    val activeAttackPlayer: PlayerId get() = attackSequence.activePlayer
    val allAttackImpulsesComplete: Boolean get() = attackSequence.isComplete
}

/**
 * Returns the attack-phase order: one block per player, loser first.
 * Players with zero units are skipped so pressing 'c' doesn't stall on an empty side.
 */
public fun calculateAttackOrder(
    loser: PlayerId,
    loserUnitCount: Int,
    winner: PlayerId,
    winnerUnitCount: Int,
): List<Impulse> = listOfNotNull(
    if (loserUnitCount > 0) Impulse(loser, loserUnitCount) else null,
    if (winnerUnitCount > 0) Impulse(winner, winnerUnitCount) else null,
)

public fun advanceAfterUnitMoved(turnState: TurnState, unitId: UnitId): TurnState {
    val updated = turnState.copy(
        movedUnitIds = turnState.movedUnitIds + unitId,
        unitsMovedInCurrentImpulse = turnState.unitsMovedInCurrentImpulse + 1,
    )
    return if (updated.remainingInImpulse == 0) {
        updated.copy(
            movementSequence = updated.movementSequence.advance(),
            unitsMovedInCurrentImpulse = 0,
        )
    } else {
        updated
    }
}

public fun selectableUnits(gameState: GameState, turnState: TurnState): List<CombatUnit> =
    gameState.unitsOf(turnState.activePlayer).filter { it.id !in turnState.movedUnitIds }

public fun selectableAttackUnits(gameState: GameState, turnState: TurnState): List<CombatUnit> =
    gameState.unitsOf(turnState.activeAttackPlayer)

public fun validateUnitSelection(unit: CombatUnit, turnState: TurnState): UnitSelectionResult = when {
    unit.owner != turnState.activePlayer -> UnitSelectionResult.NOT_YOUR_UNIT
    unit.id in turnState.movedUnitIds -> UnitSelectionResult.ALREADY_MOVED
    else -> UnitSelectionResult.VALID
}

public fun validateAttackUnitSelection(unit: CombatUnit, turnState: TurnState): UnitSelectionResult = when {
    unit.owner != turnState.activeAttackPlayer -> UnitSelectionResult.NOT_YOUR_UNIT
    else -> UnitSelectionResult.VALID
}
