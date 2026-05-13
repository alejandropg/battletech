package battletech.tui.game

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.resolveAttacks
import battletech.tactical.action.calculateMovementOrder
import battletech.tactical.action.rollInitiative
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

            is PhaseOutcome.Cancelled ->
                HandleResult(appState.copy(phase = IdlePhaseState))
        }

    /** Advance non-interactive phases (Initiative, Heat, End) or seed Attack phases. */
    public fun advanceAutomaticPhases(appState: AppState): Pair<AppState, FlashMessage?> =
        when (appState.currentPhase) {
            TurnPhase.INITIATIVE -> onInitiative(appState)
            TurnPhase.WEAPON_ATTACK -> seedAttackPhase(appState, "Weapon Attack Phase")
            TurnPhase.PHYSICAL_ATTACK -> seedAttackPhase(appState, "Physical Attack Phase")
            TurnPhase.HEAT -> onHeat(appState)
            TurnPhase.END -> onEndTurn(appState)
            TurnPhase.MOVEMENT -> appState to null
        }

    public fun commitAttackImpulse(appState: AppState): HandleResult {
        val turnState = appState.turnState
        if (turnState == null || !appState.currentPhase.isAttack) {
            return HandleResult(appState)
        }

        val (afterCommit, commitResult) = attackController.commitImpulse(turnState)
        val gameStateWithTorso = applyTorsoFacings(appState.gameState, commitResult.torsoFacings)

        val newTurnState = afterCommit.copy(
            attackSequence = afterCommit.attackSequence.advance(),
        )

        return if (newTurnState.allAttackImpulsesComplete) {
            if (appState.currentPhase == TurnPhase.WEAPON_ATTACK) {
                resolveWeaponAttacks(appState, gameStateWithTorso, newTurnState)
            } else {
                HandleResult(
                    appState.copy(
                        gameState = gameStateWithTorso,
                        currentPhase = appState.currentPhase.next,
                        phase = IdlePhaseState,
                        turnState = newTurnState,
                    )
                )
            }
        } else {
            val seededTurnState = attackController.initializeImpulse(newTurnState, newTurnState.activeAttackPlayer)
            val updatedAppState = appState.copy(
                gameState = gameStateWithTorso,
                turnState = seededTurnState,
            )
            enterFirstAttacker(updatedAppState, seededTurnState, gameStateWithTorso)
        }
    }

    internal fun enterFirstAttacker(appState: AppState, turnState: TurnState, newGameState: GameState): HandleResult {
        val units = selectableAttackUnits(newGameState, turnState)
        return if (units.isEmpty()) {
            HandleResult(appState.copy(phase = IdlePhaseState))
        } else {
            val firstUnit = units.first()
            val newPhase = attackController.enter(firstUnit, appState.currentPhase, newGameState, turnState)
            HandleResult(appState.copy(phase = newPhase, cursor = firstUnit.position))
        }
    }

    private fun onInitiative(appState: AppState): Pair<AppState, FlashMessage?> {
        val initiative = rollInitiative(random)

        val loserCount = appState.gameState.unitsOf(initiative.loser).size
        val winnerCount = appState.gameState.unitsOf(initiative.winner).size
        val movementOrder = calculateMovementOrder(
            loser = initiative.loser, loserUnitCount = loserCount,
            winner = initiative.winner, winnerUnitCount = winnerCount,
        )

        val turnState = TurnState(
            initiativeResult = initiative,
            movementSequence = ImpulseSequence(movementOrder),
        )

        val state = appState.copy(
            currentPhase = TurnPhase.INITIATIVE.next,
            turnState = turnState,
            phase = IdlePhaseState,
        )

        val p1Roll = initiative.rolls[PlayerId.PLAYER_1]!!
        val p2Roll = initiative.rolls[PlayerId.PLAYER_2]!!
        val loserName = if (initiative.loser == PlayerId.PLAYER_1) "P1" else "P2"
        val flashMessage = FlashMessage("Initiative: P1 rolled $p1Roll, P2 rolled $p2Roll — $loserName moves first")

        return state to flashMessage
    }

    private fun seedAttackPhase(appState: AppState, flashText: String): Pair<AppState, FlashMessage?> {
        val turnState = appState.turnState ?: return appState to null
        if (turnState.attackSequence.order.isNotEmpty()) return appState to null

        val seeded = turnState.copy(attackSequence = ImpulseSequence(
            attackOrderFor(
                turnState.initiativeResult,
                appState.gameState
            )
        ))
        val withImpulse = attackController.initializeImpulse(seeded, seeded.activeAttackPlayer)
        val updatedAppState = appState.copy(turnState = withImpulse)
        val result = enterFirstAttacker(updatedAppState, withImpulse, appState.gameState)
        return result.appState to FlashMessage(flashText)
    }

    private fun onHeat(appState: AppState): Pair<AppState, FlashMessage?> {
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
            currentPhase = TurnPhase.HEAT.next,
        )
        return state to FlashMessage("Heat: $details")
    }

    private fun onEndTurn(appState: AppState): Pair<AppState, FlashMessage?> {
        val resetGameState = resetTorsoFacings(appState.gameState)
        val state = appState.copy(gameState = resetGameState, currentPhase = TurnPhase.END.next)
        return state to FlashMessage("Turn complete")
    }

    private fun resolveWeaponAttacks(
        appState: AppState,
        gameStateWithTorso: GameState,
        newTurnState: TurnState,
    ): HandleResult {
        val declarations = attackController.collectDeclarations(newTurnState)
        val (resolvedGameState, flash) = if (declarations.isNotEmpty()) {
            val (resolved, results) = resolveAttacks(declarations, gameStateWithTorso, random)
            val hitCount = results.count { it.hit }
            val totalDamage = results.sumOf { it.damageApplied }
            resolved to FlashMessage("Attacks resolved: ${results.size} attacks, $hitCount hits, $totalDamage damage")
        } else {
            gameStateWithTorso to null
        }
        val cleared = attackController.clearAttackDeclarations(newTurnState)

        val physicalTurnState = cleared.copy(
            attackSequence = ImpulseSequence(emptyList()),
        )
        return HandleResult(
            appState.copy(
                gameState = resolvedGameState,
                currentPhase = TurnPhase.WEAPON_ATTACK.next,
                phase = IdlePhaseState,
                turnState = physicalTurnState,
            ),
            flash,
        )
    }

    private fun handleComplete(outcome: PhaseOutcome.Complete, appState: AppState): HandleResult {
        val turnState = appState.turnState ?: return HandleResult(
            appState.copy(
                gameState = outcome.gameState,
                currentPhase = appState.currentPhase.next,
                phase = IdlePhaseState,
            )
        )

        return when (appState.currentPhase) {
            TurnPhase.MOVEMENT -> onMovementComplete(outcome, appState, turnState)
            TurnPhase.WEAPON_ATTACK, TurnPhase.PHYSICAL_ATTACK -> onAttackComplete(outcome, appState)
            else -> HandleResult(
                appState.copy(
                    gameState = outcome.gameState,
                    currentPhase = appState.currentPhase.next,
                    phase = IdlePhaseState,
                )
            )
        }
    }

    private fun onMovementComplete(
        outcome: PhaseOutcome.Complete,
        appState: AppState,
        turnState: TurnState,
    ): HandleResult {
        val movedUnitId = outcome.movedUnitId
            ?: error("MovementController must set movedUnitId on Complete outcomes")
        val newTurnState = advanceAfterUnitMoved(turnState, movedUnitId)
        return if (newTurnState.allImpulsesComplete) {
            val withAttack = newTurnState.copy(
                attackSequence = ImpulseSequence(attackOrderFor(newTurnState.initiativeResult, outcome.gameState)),
            )
            HandleResult(
                appState.copy(
                    gameState = outcome.gameState,
                    currentPhase = appState.currentPhase.next,
                    phase = IdlePhaseState,
                    turnState = withAttack,
                )
            )
        } else {
            HandleResult(
                appState.copy(
                    gameState = outcome.gameState,
                    phase = IdlePhaseState,
                    turnState = newTurnState,
                )
            )
        }
    }

    private fun onAttackComplete(outcome: PhaseOutcome.Complete, appState: AppState): HandleResult =
        HandleResult(
            appState.copy(
                gameState = outcome.gameState,
                currentPhase = appState.currentPhase.next,
                phase = IdlePhaseState,
            )
        )
}
