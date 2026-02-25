package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public data class AppState(
    val gameState: GameState,
    val currentPhase: TurnPhase,
    val cursor: HexCoordinates,
    val phaseState: PhaseState,
)

public data class FlashMessage(val text: String)

public fun nextPhase(phase: TurnPhase): TurnPhase = when (phase) {
    TurnPhase.INITIATIVE -> TurnPhase.MOVEMENT
    TurnPhase.MOVEMENT -> TurnPhase.WEAPON_ATTACK
    TurnPhase.WEAPON_ATTACK -> TurnPhase.PHYSICAL_ATTACK
    TurnPhase.PHYSICAL_ATTACK -> TurnPhase.HEAT
    TurnPhase.HEAT -> TurnPhase.END
    TurnPhase.END -> TurnPhase.INITIATIVE
}

public fun handlePhaseOutcome(outcome: PhaseOutcome, appState: AppState): AppState = when (outcome) {
    is PhaseOutcome.Continue -> appState.copy(phaseState = outcome.phaseState)
    is PhaseOutcome.Complete -> appState.copy(
        gameState = outcome.gameState,
        currentPhase = nextPhase(appState.currentPhase),
        phaseState = PhaseState.Idle(),
    )
    is PhaseOutcome.Cancelled -> appState.copy(phaseState = PhaseState.Idle())
}

public fun moveCursor(cursor: HexCoordinates, direction: HexDirection, map: GameMap): HexCoordinates {
    val neighbor = cursor.neighbor(direction)
    return if (neighbor in map.hexes) neighbor else cursor
}

public fun autoAdvanceGlobalPhases(appState: AppState): Pair<AppState, FlashMessage?> {
    return when (appState.currentPhase) {
        TurnPhase.INITIATIVE -> {
            val state = appState.copy(currentPhase = nextPhase(TurnPhase.INITIATIVE))
            state to FlashMessage("Initiative resolved")
        }
        TurnPhase.HEAT -> {
            val oldUnits = appState.gameState.units
            val newGameState = applyHeatDissipation(appState.gameState)
            val details = oldUnits.zip(newGameState.units)
                .filter { (old, _) -> old.currentHeat > 0 }
                .joinToString(", ") { (old, new) ->
                    "${old.name}: ${old.currentHeat}→${new.currentHeat}"
                }
                .ifEmpty { "No heat to dissipate" }
            val state = appState.copy(
                gameState = newGameState,
                currentPhase = nextPhase(TurnPhase.HEAT),
            )
            state to FlashMessage("Heat: $details")
        }
        TurnPhase.END -> {
            val state = appState.copy(currentPhase = nextPhase(TurnPhase.END))
            state to FlashMessage("Turn complete")
        }
        else -> appState to null
    }
}

public fun applyHeatDissipation(gameState: GameState): GameState {
    val updatedUnits = gameState.units.map { unit ->
        val newHeat = maxOf(0, unit.currentHeat - unit.heatSinkCapacity)
        unit.copy(currentHeat = newHeat)
    }
    return gameState.copy(units = updatedUnits)
}
