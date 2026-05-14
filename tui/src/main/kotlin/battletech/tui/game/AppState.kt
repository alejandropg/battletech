package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tui.game.phase.Phase

public data class AppState(
    val gameState: GameState,
    val cursor: HexCoordinates,
    val phase: Phase,
    val turnState: TurnState? = null,
) {
    public val currentPhase: TurnPhase get() = phase.turnPhase
}

public fun moveCursor(
    cursor: HexCoordinates,
    direction: HexDirection,
    map: GameMap,
): HexCoordinates {
    val neighbor = cursor.neighbor(direction)
    return if (neighbor in map.hexes) neighbor else cursor
}
