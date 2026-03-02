package battletech.tui.game

import battletech.tactical.action.TurnPhase
import kotlin.random.Random

public class PhaseManager(
    public val movementController: MovementController,
    public val attackController: AttackController,
    public val random: Random = Random,
) {

    public fun fromOutcome(outcome: PhaseOutcome, appState: AppState): HandleResult =
        when (outcome) {
            is PhaseOutcome.Continue ->
                HandleResult(appState.copy(phase = outcome.phaseState))

            is PhaseOutcome.Complete ->
                handleComplete(outcome, appState)

            is PhaseOutcome.Cancelled -> {
                val prompt = contextualIdlePrompt(appState)
                HandleResult(appState.copy(phase = IdlePhaseState(prompt)))
            }
        }

    internal fun contextualIdlePrompt(appState: AppState): String {
        val prompt = when (appState.currentPhase) {
            TurnPhase.WEAPON_ATTACK -> appState.turnState?.let { attackPrompt(it) }
            TurnPhase.PHYSICAL_ATTACK -> appState.turnState?.let { attackPrompt(it) }
            TurnPhase.MOVEMENT -> appState.turnState?.let { movementPrompt(it) }
            else -> null
        }
        return prompt ?: "Move cursor to select a unit"
    }

    private fun handleComplete(outcome: PhaseOutcome.Complete, appState: AppState): HandleResult {
        val turnState = appState.turnState ?: return HandleResult(
            appState.copy(
                gameState = outcome.gameState,
                currentPhase = nextPhase(appState.currentPhase),
                phase = IdlePhaseState(),
            )
        )

        return when (appState.currentPhase) {
            TurnPhase.MOVEMENT -> {
                val movedUnitId = findMovedUnit(appState.gameState, outcome.gameState)
                val newTurnState = advanceAfterUnitMoved(turnState, movedUnitId)
                if (newTurnState.allImpulsesComplete) {
                    val attackOrder = newTurnState.movementOrder
                    val withAttack = newTurnState.copy(attackOrder = attackOrder)
                    HandleResult(
                        appState.copy(
                            gameState = outcome.gameState,
                            currentPhase = nextPhase(appState.currentPhase),
                            phase = IdlePhaseState(attackPrompt(withAttack)),
                            turnState = withAttack,
                        )
                    )
                } else {
                    HandleResult(
                        appState.copy(
                            gameState = outcome.gameState,
                            phase = IdlePhaseState(movementPrompt(newTurnState)),
                            turnState = newTurnState,
                        )
                    )
                }
            }

            TurnPhase.WEAPON_ATTACK, TurnPhase.PHYSICAL_ATTACK -> HandleResult(
                appState.copy(
                    gameState = outcome.gameState,
                    currentPhase = nextPhase(appState.currentPhase),
                    phase = IdlePhaseState(),
                )
            )

            else -> HandleResult(
                appState.copy(
                    gameState = outcome.gameState,
                    currentPhase = nextPhase(appState.currentPhase),
                    phase = IdlePhaseState(),
                )
            )
        }
    }
}
