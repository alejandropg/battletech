package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameState

public sealed interface GameLoopResult {
    public data class Continue(val state: GameState) : GameLoopResult
    public data class PhaseComplete(val newState: GameState, val nextPhase: TurnPhase) : GameLoopResult
    public data object Quit : GameLoopResult
}
