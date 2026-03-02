package battletech.tui.game

import battletech.tactical.action.TurnPhase

public class PhaseManager(
    public val movementController: MovementController,
    public val attackController: AttackController,
) {
    public fun idle(prompt: String = "Move cursor to select a unit"): ActivePhase =
        IdlePhase(this, PhaseState.Idle(prompt))

    public fun browsing(state: PhaseState.Movement.Browsing): ActivePhase =
        BrowsingPhase(this, state)

    public fun facing(state: PhaseState.Movement.SelectingFacing): ActivePhase =
        FacingPhase(this, state)

    public fun attack(state: PhaseState.Attack): ActivePhase =
        AttackPhase(this, state)

    public fun wrap(phaseState: PhaseState): ActivePhase = when (phaseState) {
        is PhaseState.Idle -> idle(phaseState.prompt)
        is PhaseState.Movement.Browsing -> browsing(phaseState)
        is PhaseState.Movement.SelectingFacing -> facing(phaseState)
        is PhaseState.Attack -> attack(phaseState)
    }

    public fun fromOutcome(outcome: PhaseOutcome, appState: AppState): HandleResult =
        when (outcome) {
            is PhaseOutcome.Continue ->
                HandleResult(appState.copy(phase = wrap(outcome.phaseState)))

            is PhaseOutcome.Complete ->
                handleComplete(outcome, appState)

            is PhaseOutcome.Cancelled -> {
                val prompt = contextualIdlePrompt(appState)
                HandleResult(appState.copy(phase = idle(prompt)))
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
                phase = idle(),
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
                            phase = idle(attackPrompt(withAttack)),
                            turnState = withAttack,
                        )
                    )
                } else {
                    HandleResult(
                        appState.copy(
                            gameState = outcome.gameState,
                            phase = idle(movementPrompt(newTurnState)),
                            turnState = newTurnState,
                        )
                    )
                }
            }

            TurnPhase.WEAPON_ATTACK, TurnPhase.PHYSICAL_ATTACK -> HandleResult(
                appState.copy(
                    gameState = outcome.gameState,
                    currentPhase = nextPhase(appState.currentPhase),
                    phase = idle(),
                )
            )

            else -> HandleResult(
                appState.copy(
                    gameState = outcome.gameState,
                    currentPhase = nextPhase(appState.currentPhase),
                    phase = idle(),
                )
            )
        }
    }
}
