package battletech.tactical.movement

import battletech.tactical.model.MovementMode

import battletech.tactical.model.GameState
import battletech.tactical.unit.CombatUnit

public data class MovementContext(
    public val actor: CombatUnit,
    public val gameState: GameState,
    public val movementMode: MovementMode,
)
