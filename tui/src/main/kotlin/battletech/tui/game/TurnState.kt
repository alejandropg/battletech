package battletech.tui.game

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.Impulse
import battletech.tactical.action.Initiative
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackDeclaration
import battletech.tactical.model.GameState

public data class TurnState(
    val initiative: Initiative,
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

    public fun advanceAfterUnitMoved(unitId: UnitId): TurnState {
        val updated = copy(
            movedUnitIds = movedUnitIds + unitId,
            unitsMovedInCurrentImpulse = unitsMovedInCurrentImpulse + 1,
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

    public fun selectableUnits(gameState: GameState): List<CombatUnit> =
        gameState.unitsOf(activePlayer).filter { it.id !in movedUnitIds }

    public fun selectableAttackUnits(gameState: GameState): List<CombatUnit> =
        gameState.unitsOf(activeAttackPlayer)

    public companion object {
        public val NULL: TurnState = TurnState(
            Initiative(emptyMap(), PlayerId.PLAYER_1, PlayerId.PLAYER_2),
            ImpulseSequence(emptyList()),
        )
    }
}
