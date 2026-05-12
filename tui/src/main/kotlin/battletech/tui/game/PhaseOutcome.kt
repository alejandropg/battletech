package battletech.tui.game

import battletech.tactical.action.UnitId
import battletech.tactical.model.GameState

public sealed interface PhaseOutcome {
    public data class Continue(val phaseState: PhaseState) : PhaseOutcome
    public data class Complete(val gameState: GameState, val movedUnitId: UnitId? = null) : PhaseOutcome
    public data object Cancelled : PhaseOutcome
}
