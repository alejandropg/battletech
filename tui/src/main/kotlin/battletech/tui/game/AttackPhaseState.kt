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

        val outcome = phaseManager.attackController.handle(action, this, appState.cursor, appState.gameState)
        val result = phaseManager.fromOutcome(outcome, appState)

        // After returning to Idle from attack, refresh prompt with declaration progress
        val newPhaseState = result.appState.phase
        if (newPhaseState is IdlePhaseState && isAttackPhase(result.appState.currentPhase)) {
            val ts = result.appState.turnState
            if (ts != null && !ts.allAttackImpulsesComplete) {
                return HandleResult(
                    result.appState.copy(phase = IdlePhaseState(buildAttackPrompt(ts, phaseManager))),
                    result.flash,
                )
            }
        }
        return result
    }
}
