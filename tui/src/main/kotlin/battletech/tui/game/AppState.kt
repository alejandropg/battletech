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
    val phaseState: PhaseState,
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

public fun handlePhaseOutcome(outcome: PhaseOutcome, appState: AppState): AppState = when (outcome) {
    is PhaseOutcome.Continue -> appState.copy(phaseState = outcome.phaseState)
    is PhaseOutcome.Complete -> handleComplete(outcome, appState)
    is PhaseOutcome.Cancelled -> {
        val prompt = when (appState.currentPhase) {
            TurnPhase.WEAPON_ATTACK -> appState.turnState?.let { attackPrompt(it) }
            TurnPhase.PHYSICAL_ATTACK -> appState.turnState?.let { attackPrompt(it) }
            TurnPhase.MOVEMENT -> appState.turnState?.let { movementPrompt(it) }
            else -> null
        }
        appState.copy(phaseState = PhaseState.Idle(prompt ?: "Move cursor to select a unit"))
    }
}

private fun handleComplete(outcome: PhaseOutcome.Complete, appState: AppState): AppState {
    val turnState = appState.turnState ?: return appState.copy(
        gameState = outcome.gameState,
        currentPhase = nextPhase(appState.currentPhase),
        phaseState = PhaseState.Idle(),
    )

    return when (appState.currentPhase) {
        TurnPhase.MOVEMENT -> {
            val movedUnitId = findMovedUnit(appState.gameState, outcome.gameState)
            val newTurnState = advanceAfterUnitMoved(turnState, movedUnitId)
            if (newTurnState.allImpulsesComplete) {
                val attackOrder = newTurnState.movementOrder
                val withAttack = newTurnState.copy(attackOrder = attackOrder)
                appState.copy(
                    gameState = outcome.gameState,
                    currentPhase = nextPhase(appState.currentPhase),
                    phaseState = PhaseState.Idle(attackPrompt(withAttack)),
                    turnState = withAttack,
                )
            } else {
                appState.copy(
                    gameState = outcome.gameState,
                    phaseState = PhaseState.Idle(movementPrompt(newTurnState)),
                    turnState = newTurnState,
                )
            }
        }
        TurnPhase.WEAPON_ATTACK, TurnPhase.PHYSICAL_ATTACK -> {
            // Attack completed for one unit — find the unit that just declared
            val attackState = appState.phaseState as? PhaseState.Attack
            val unitId = attackState?.unitId ?: turnState.attackedUnitIds.lastOrNull()
                ?: return appState.copy(
                    gameState = outcome.gameState,
                    currentPhase = nextPhase(appState.currentPhase),
                    phaseState = PhaseState.Idle(),
                )

            val newTurnState = advanceAfterUnitAttacked(turnState, unitId)
            if (newTurnState.allAttackImpulsesComplete) {
                appState.copy(
                    gameState = outcome.gameState,
                    currentPhase = nextPhase(appState.currentPhase),
                    phaseState = PhaseState.Idle(),
                    turnState = if (appState.currentPhase == TurnPhase.WEAPON_ATTACK) {
                        // Reset attack tracking for physical attack phase
                        newTurnState.copy(
                            attackedUnitIds = emptySet(),
                            currentAttackImpulseIndex = 0,
                            unitsAttackedInCurrentImpulse = 0,
                        )
                    } else {
                        newTurnState
                    },
                )
            } else {
                appState.copy(
                    gameState = outcome.gameState,
                    phaseState = PhaseState.Idle(attackPrompt(newTurnState)),
                    turnState = newTurnState,
                )
            }
        }
        else -> appState.copy(
            gameState = outcome.gameState,
            currentPhase = nextPhase(appState.currentPhase),
            phaseState = PhaseState.Idle(),
        )
    }
}

public fun moveCursor(cursor: HexCoordinates, direction: HexDirection, map: battletech.tactical.model.GameMap): HexCoordinates {
    val neighbor = cursor.neighbor(direction)
    return if (neighbor in map.hexes) neighbor else cursor
}

public fun autoAdvanceGlobalPhases(appState: AppState, random: Random = Random): Pair<AppState, FlashMessage?> {
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
                phaseState = PhaseState.Idle(prompt),
            )
            state to FlashMessage("Initiative: P1 rolled $p1Roll, P2 rolled $p2Roll — $loserName moves first")
        }
        TurnPhase.WEAPON_ATTACK -> {
            // Initialize attack order if not already set
            val turnState = appState.turnState
            if (turnState != null && turnState.attackOrder.isEmpty()) {
                val newTurnState = turnState.copy(attackOrder = turnState.movementOrder)
                val state = appState.copy(
                    turnState = newTurnState,
                    phaseState = PhaseState.Idle(attackPrompt(newTurnState)),
                )
                state to FlashMessage("Weapon Attack Phase")
            } else {
                appState to null
            }
        }
        TurnPhase.PHYSICAL_ATTACK -> {
            val turnState = appState.turnState
            if (turnState != null && turnState.attackOrder.isEmpty()) {
                val newTurnState = turnState.copy(attackOrder = turnState.movementOrder)
                val state = appState.copy(
                    turnState = newTurnState,
                    phaseState = PhaseState.Idle(attackPrompt(newTurnState)),
                )
                state to FlashMessage("Physical Attack Phase")
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

public fun movementPrompt(turnState: TurnState): String {
    val playerName = if (turnState.activePlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
    val remaining = turnState.remainingInImpulse
    return "$playerName: select a unit to move ($remaining remaining)"
}

public fun attackPrompt(turnState: TurnState): String {
    if (turnState.allAttackImpulsesComplete) return "All attacks declared"
    val playerName = if (turnState.activeAttackPlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
    val remaining = turnState.remainingInAttackImpulse
    return "$playerName: select a unit to attack ($remaining remaining)"
}

private fun findMovedUnit(oldState: GameState, newState: GameState): UnitId {
    for ((old, new) in oldState.units.zip(newState.units)) {
        if (old.position != new.position || old.facing != new.facing) {
            return old.id
        }
    }
    return oldState.units.first().id
}
