package battletech.tui.game

import battletech.tui.input.InputAction
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.model.GameState

public class InitiativePhaseController : PhaseController {

    override fun enter(unit: Unit, gameState: GameState): PhaseState {
        return PhaseState(
            phase = TurnPhase.INITIATIVE,
            selectedUnitId = unit.id,
            prompt = "Initiative resolved. Press Enter to continue.",
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
