package battletech.tui.game

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.calculateMovementOrder
import battletech.tactical.action.rollInitiative
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import kotlin.random.Random

public data class AppState(
    val gameState: GameState,
    val currentPhase: TurnPhase,
    val cursor: HexCoordinates,
    val phase: PhaseState,
    val turnState: TurnState? = null,
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

public fun moveCursor(
    cursor: HexCoordinates,
    direction: HexDirection,
    map: battletech.tactical.model.GameMap
): HexCoordinates {
    val neighbor = cursor.neighbor(direction)
    return if (neighbor in map.hexes) neighbor else cursor
}

public fun autoAdvanceGlobalPhases(
    appState: AppState,
    phaseManager: PhaseManager,
    random: Random = Random,
): Pair<AppState, FlashMessage?> {
    return when (appState.currentPhase) {
        TurnPhase.INITIATIVE -> {
            val result = rollInitiative(random)
            val p1Roll = result.rolls[PlayerId.PLAYER_1]!!
            val p2Roll = result.rolls[PlayerId.PLAYER_2]!!
            val loserName = if (result.loser == PlayerId.PLAYER_1) "P1" else "P2"

            val loserCount = appState.gameState.unitsOf(result.loser).size
            val winnerCount = appState.gameState.unitsOf(result.winner).size
            val movementOrder = calculateMovementOrder(
                loser = result.loser, loserUnitCount = loserCount,
                winner = result.winner, winnerUnitCount = winnerCount,
            )

            val turnState = TurnState(
                initiativeResult = result,
                movementOrder = movementOrder,
            )

            val prompt = movementPrompt(turnState)
            val state = appState.copy(
                currentPhase = nextPhase(TurnPhase.INITIATIVE),
                turnState = turnState,
                phase = IdlePhaseState(prompt),
            )
            state to FlashMessage("Initiative: P1 rolled $p1Roll, P2 rolled $p2Roll — $loserName moves first")
        }

        TurnPhase.WEAPON_ATTACK -> {
            val turnState = appState.turnState
            if (turnState != null && turnState.attackOrder.isEmpty()) {
                val newTurnState = turnState.copy(attackOrder = attackOrderFor(turnState, appState.gameState))
                phaseManager.attackController.initializeImpulse(newTurnState.activeAttackPlayer)
                val updatedAppState = appState.copy(turnState = newTurnState)
                val result = phaseManager.enterFirstAttacker(updatedAppState, newTurnState, appState.gameState)
                result.appState to FlashMessage("Weapon Attack Phase")
            } else {
                appState to null
            }
        }

        TurnPhase.PHYSICAL_ATTACK -> {
            val turnState = appState.turnState
            if (turnState != null && turnState.attackOrder.isEmpty()) {
                val newTurnState = turnState.copy(attackOrder = attackOrderFor(turnState, appState.gameState))
                phaseManager.attackController.initializeImpulse(newTurnState.activeAttackPlayer)
                val updatedAppState = appState.copy(turnState = newTurnState)
                val result = phaseManager.enterFirstAttacker(updatedAppState, newTurnState, appState.gameState)
                result.appState to FlashMessage("Physical Attack Phase")
            } else {
                appState to null
            }
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
            val resetGameState = resetTorsoFacings(appState.gameState)
            val state = appState.copy(gameState = resetGameState, currentPhase = nextPhase(TurnPhase.END))
            state to FlashMessage("Turn complete")
        }

        else -> appState to null
    }
}

public fun applyTorsoFacings(gameState: GameState, facings: Map<UnitId, HexDirection>): GameState {
    if (facings.isEmpty()) return gameState
    val updatedUnits = gameState.units.map { unit ->
        val torso = facings[unit.id]
        if (torso != null) unit.copy(torsoFacing = torso) else unit
    }
    return gameState.copy(units = updatedUnits)
}

public fun resetTorsoFacings(gameState: GameState): GameState {
    val updatedUnits = gameState.units.map { unit ->
        if (unit.torsoFacing != unit.facing) unit.copy(torsoFacing = unit.facing) else unit
    }
    return gameState.copy(units = updatedUnits)
}

public fun applyHeatDissipation(gameState: GameState): GameState {
    val updatedUnits = gameState.units.map { unit ->
        val newHeat = maxOf(0, unit.currentHeat - unit.heatSinkCapacity)
        unit.copy(currentHeat = newHeat)
    }
    return gameState.copy(units = updatedUnits)
}

public fun movementPrompt(turnState: TurnState): String {
    val playerName = if (turnState.activePlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
    val remaining = turnState.remainingInImpulse
    return "$playerName: select a unit to move ($remaining remaining)"
}

public fun attackPrompt(turnState: TurnState): String {
    if (turnState.allAttackImpulsesComplete) return "All attacks declared"
    val playerName = if (turnState.activeAttackPlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
    return "$playerName: select units, toggle weapons | 'c' to commit"
}

internal fun attackOrderFor(turnState: TurnState, gameState: GameState): List<battletech.tactical.action.MovementImpulse> {
    val loser = turnState.initiativeResult.loser
    val winner = turnState.initiativeResult.winner
    return calculateAttackOrder(
        loser = loser,
        loserUnitCount = gameState.unitsOf(loser).size,
        winner = winner,
        winnerUnitCount = gameState.unitsOf(winner).size,
    )
}

internal fun findMovedUnit(oldState: GameState, newState: GameState): UnitId {
    for ((old, new) in oldState.units.zip(newState.units)) {
        if (old.position != new.position || old.facing != new.facing) {
            return old.id
        }
    }
    return oldState.units.first().id
}
