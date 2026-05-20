package battletech.tactical.session

import battletech.tactical.model.GameState

/**
 * The triple a [PhaseHandler] returns from [PhaseHandler.apply] or
 * [PhaseHandler.onEntry]: the new authoritative state, the new turn-tracking
 * state, and the events that explain what happened.
 */
public data class PhaseOutcome(
    public val state: GameState,
    public val turn: TurnState,
    public val events: List<GameEvent>,
)
