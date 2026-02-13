package battletech.tui.game

import battletech.tui.input.InputAction
import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.model.GameState

public class AttackPhaseController(
    private val actionQueryService: ActionQueryService,
    private val phase: TurnPhase,
) : PhaseController {

    override fun enter(unit: Unit, gameState: GameState): PhaseState {
        val report = actionQueryService.getAttackActions(unit, phase, gameState)
        val available = report.actions.filterIsInstance<AvailableAction>()

        return PhaseState(
            phase = phase,
            selectedUnitId = unit.id,
            prompt = if (available.isEmpty()) {
                "No attacks available. Press Enter to skip."
            } else {
                "Select attack for ${unit.name}"
            },
        )
    }

    override fun handleAction(
        action: InputAction,
        phaseState: PhaseState,
        gameState: GameState,
    ): PhaseControllerResult {
        return when (action) {
            is InputAction.Cancel -> PhaseControllerResult.Cancelled
            is InputAction.Confirm -> {
                // For now, confirm skips the phase (no attack selection implemented yet)
                PhaseControllerResult.UpdateState(phaseState)
            }
            else -> PhaseControllerResult.UpdateState(phaseState)
        }
    }
}
