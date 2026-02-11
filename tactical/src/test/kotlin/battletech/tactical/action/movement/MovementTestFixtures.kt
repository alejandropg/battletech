package battletech.tactical.action.movement

import battletech.tactical.action.Unit
import battletech.tactical.action.aGameState
import battletech.tactical.action.aUnit
import battletech.tactical.model.GameState
import battletech.tactical.model.MovementMode

internal fun aMovementContext(
    actor: Unit = aUnit(),
    gameState: GameState = aGameState(),
    movementMode: MovementMode = MovementMode.WALK,
): MovementContext = MovementContext(
    actor = actor,
    gameState = gameState,
    movementMode = movementMode,
)
