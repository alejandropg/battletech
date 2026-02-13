package battletech.tui.game

import battletech.tui.input.InputAction
import battletech.tactical.action.Unit
import battletech.tactical.model.GameState

public interface PhaseController {
    public fun enter(unit: Unit, gameState: GameState): PhaseState
    public fun handleAction(action: InputAction, phaseState: PhaseState, gameState: GameState): PhaseControllerResult
}

public sealed interface PhaseControllerResult {
    public data class UpdateState(val phaseState: PhaseState) : PhaseControllerResult
    public data class Complete(val updatedGameState: GameState) : PhaseControllerResult
    public data object Cancelled : PhaseControllerResult
}
