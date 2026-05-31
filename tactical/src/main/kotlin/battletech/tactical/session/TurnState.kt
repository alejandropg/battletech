package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.physical.PhysicalAttackDeclaration
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId

public data class TurnState(
    val initiative: Initiative,
    val movementSequence: ImpulseSequence = ImpulseSequence(emptyList()),
    val movedUnitIds: Set<UnitId> = emptySet(),
    val unitsMovedInCurrentImpulse: Int = 0,
    val attackSequence: ImpulseSequence = ImpulseSequence(emptyList()),
    val attackDeclarations: List<AttackDeclaration> = emptyList(),
    val physicalAttackDeclarations: List<PhysicalAttackDeclaration> = emptyList(),
    val turnNumber: Int = 1,
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
        )
    }
}
