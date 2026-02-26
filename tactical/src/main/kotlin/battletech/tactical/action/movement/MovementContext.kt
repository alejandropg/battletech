package battletech.tactical.action.movement

import battletech.tactical.action.CombatUnit
import battletech.tactical.model.GameState
import battletech.tactical.model.MovementMode

public data class MovementContext(
    public val actor: CombatUnit,
    public val gameState: GameState,
    public val movementMode: MovementMode,
)
