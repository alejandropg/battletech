package battletech.tui.game

import battletech.tactical.model.GameState

public sealed interface PhaseOutcome {
    public data class Continue(val phaseState: PhaseState) : PhaseOutcome
    public data class Complete(val gameState: GameState) : PhaseOutcome
    public data object Cancelled : PhaseOutcome
}
