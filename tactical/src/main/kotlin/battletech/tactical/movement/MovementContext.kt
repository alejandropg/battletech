package battletech.tactical.movement

import battletech.tactical.model.CombatUnit
import battletech.tactical.model.GameState

public data class MovementContext(
    public val actor: CombatUnit,
    public val gameState: GameState,
    public val movementMode: MovementMode,
)
