package battletech.tui.game

import battletech.tui.input.InputAction
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.model.GameState

public class EndPhaseController : PhaseController {

    override fun enter(unit: Unit, gameState: GameState): PhaseState {
        return PhaseState(
            phase = TurnPhase.END,
            selectedUnitId = unit.id,
            prompt = "End phase. Press Enter to start next turn.",
        )
    }

    override fun handleAction(
        action: InputAction,
        phaseState: PhaseState,
        gameState: GameState,
    ): PhaseControllerResult {
        return when (action) {
            is InputAction.Confirm -> PhaseControllerResult.Complete(gameState)
            is InputAction.Cancel -> PhaseControllerResult.Cancelled
            else -> PhaseControllerResult.UpdateState(phaseState)
        }
    }
}
