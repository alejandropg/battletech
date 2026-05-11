package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.resolveAttacks
import battletech.tactical.model.GameState
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

    public fun commitAttackImpulse(appState: AppState): HandleResult {
        val turnState = appState.turnState
        if (turnState == null || !isAttackPhase(appState.currentPhase)) {
            return HandleResult(appState)
        }

        val commitResult = attackController.commitImpulse()
        val gameStateWithTorso = applyTorsoFacings(appState.gameState, commitResult.torsoFacings)

        val newTurnState = turnState.copy(
            currentAttackImpulseIndex = turnState.currentAttackImpulseIndex + 1,
        )

        return if (newTurnState.allAttackImpulsesComplete) {
            if (appState.currentPhase == TurnPhase.WEAPON_ATTACK) {
                resolveWeaponAttacks(appState, gameStateWithTorso, newTurnState)
            } else {
                HandleResult(
                    appState.copy(
                        gameState = gameStateWithTorso,
                        currentPhase = nextPhase(appState.currentPhase),
                        phase = IdlePhaseState(),
                        turnState = newTurnState,
                    )
                )
            }
        } else {
            attackController.initializeImpulse(newTurnState.activeAttackPlayer)
            val updatedAppState = appState.copy(
                gameState = gameStateWithTorso,
                turnState = newTurnState,
            )
            enterFirstAttacker(updatedAppState, newTurnState, gameStateWithTorso)
        }
    }

    internal fun enterFirstAttacker(appState: AppState, turnState: TurnState, newGameState: GameState): HandleResult {
        val units = selectableAttackUnits(newGameState, turnState)
        return if (units.isEmpty()) {
            HandleResult(appState.copy(phase = IdlePhaseState(attackPrompt(turnState))))
        } else {
            val firstUnit = units.first()
            val newPhase = attackController.enter(firstUnit, appState.currentPhase, newGameState)
            HandleResult(appState.copy(phase = newPhase, cursor = firstUnit.position))
        }
    }

    private fun resolveWeaponAttacks(
        appState: AppState,
        gameStateWithTorso: GameState,
        newTurnState: TurnState,
    ): HandleResult {
        val declarations = attackController.collectDeclarations()
        val (resolvedGameState, flash) = if (declarations.isNotEmpty()) {
            val (resolved, results) = resolveAttacks(declarations, gameStateWithTorso, random)
            val hitCount = results.count { it.hit }
            val totalDamage = results.sumOf { it.damageApplied }
            resolved to FlashMessage("Attacks resolved: ${results.size} attacks, $hitCount hits, $totalDamage damage")
        } else {
            gameStateWithTorso to null
        }
        attackController.clearDeclarations()

        val physicalTurnState = newTurnState.copy(
            currentAttackImpulseIndex = 0,
            attackOrder = emptyList(),
        )
        return HandleResult(
            appState.copy(
                gameState = resolvedGameState,
                currentPhase = nextPhase(TurnPhase.WEAPON_ATTACK),
                phase = IdlePhaseState(),
                turnState = physicalTurnState,
            ),
            flash,
        )
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
                    val withAttack = newTurnState.copy(
                        attackOrder = attackOrderFor(newTurnState, outcome.gameState),
                    )
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
