package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tui.input.AttackAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

/** Player twists torso and assigns weapons simultaneously. */
public data class AttackPhaseState(
    val unitId: UnitId,
    val attackPhase: TurnPhase,
    val torsoFacing: HexDirection,
    val arc: Set<HexCoordinates>,
    val validTargetIds: Set<UnitId>,
    val targets: List<TargetInfo>,
    val cursorTargetIndex: Int,
    val cursorWeaponIndex: Int,
    val weaponAssignments: Map<UnitId, Set<Int>>,
    val primaryTargetId: UnitId?,
    override val prompt: String,
) : PhaseState {

    override fun processEvent(
        event: InputEvent,
        appState: AppState,
        phaseManager: PhaseManager,
    ): HandleResult? {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapAttackEvent(event)
            is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                ?.let { AttackAction.ClickTarget(it) }
        } ?: return null

        if (action is AttackAction.NextAttacker) {
            return cycleToNextAttacker(appState, phaseManager)
        }

        val outcome = phaseManager.attackController.handle(action, this, appState.cursor, appState.gameState)
        return phaseManager.fromOutcome(outcome, appState)
    }

    private fun cycleToNextAttacker(appState: AppState, phaseManager: PhaseManager): HandleResult {
        val turn = appState.turnState
            ?: return phaseManager.fromOutcome(PhaseOutcome.Cancelled, appState)
        val attackers = selectableAttackUnits(appState.gameState, turn)
        if (attackers.isEmpty()) {
            return phaseManager.fromOutcome(PhaseOutcome.Cancelled, appState)
        }
        val currentIdx = attackers.indexOfFirst { it.id == this.unitId }.coerceAtLeast(0)
        val nextIdx = (currentIdx + 1) % attackers.size
        val nextUnit = attackers[nextIdx]
        val newPhase = phaseManager.attackController.enter(nextUnit, this.attackPhase, appState.gameState)
        return HandleResult(appState.copy(phase = newPhase, cursor = nextUnit.position))
    }
}
