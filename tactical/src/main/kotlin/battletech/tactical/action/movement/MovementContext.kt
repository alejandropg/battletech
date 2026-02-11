package battletech.tactical.action.movement

import battletech.tactical.action.Unit
import battletech.tactical.model.GameState
import battletech.tactical.model.MovementMode

public data class MovementContext(
    public val actor: Unit,
    public val gameState: GameState,
    public val movementMode: MovementMode,
)
