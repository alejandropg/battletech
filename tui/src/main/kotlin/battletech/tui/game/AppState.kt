package battletech.tui.game

import battletech.tactical.action.Impulse
import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public data class AppState(
    val gameState: GameState,
    val currentPhase: TurnPhase,
    val cursor: HexCoordinates,
    val phase: PhaseState,
    val turnState: TurnState? = null,
)

public data class FlashMessage(val text: String)

public fun moveCursor(
    cursor: HexCoordinates,
    direction: HexDirection,
    map: battletech.tactical.model.GameMap
): HexCoordinates {
    val neighbor = cursor.neighbor(direction)
    return if (neighbor in map.hexes) neighbor else cursor
}

internal fun attackOrderFor(turnState: TurnState, gameState: GameState): List<Impulse> {
    val loser = turnState.initiativeResult.loser
    val winner = turnState.initiativeResult.winner
    return calculateAttackOrder(
        loser = loser,
        loserUnitCount = gameState.unitsOf(loser).size,
        winner = winner,
        winnerUnitCount = gameState.unitsOf(winner).size,
    )
}
