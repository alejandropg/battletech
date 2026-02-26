package battletech.tactical.action.movement

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.aGameState
import battletech.tactical.action.aUnit
import battletech.tactical.model.GameState
import battletech.tactical.model.MovementMode

internal fun aMovementContext(
    actor: CombatUnit = aUnit(),
    gameState: GameState = aGameState(),
    movementMode: MovementMode = MovementMode.WALK,
): MovementContext = MovementContext(
    actor = actor,
    gameState = gameState,
    movementMode = movementMode,
)
