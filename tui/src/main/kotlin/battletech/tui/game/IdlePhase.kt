package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.resolveAttacks
import battletech.tui.input.IdleAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import kotlin.random.Random

public class IdlePhase(
    private val manager: PhaseManager,
    override val state: PhaseState.Idle,
    private val random: Random = Random,
) : ActivePhase {

    override fun processEvent(event: InputEvent, appState: AppState): HandleResult? {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapIdleEvent(event)
            is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                ?.let { IdleAction.ClickHex(it) }
        } ?: return null

        return when (action) {
            is IdleAction.MoveCursor -> {
                val newCursor = moveCursor(appState.cursor, action.direction, appState.gameState.map)
                HandleResult(appState.copy(cursor = newCursor))
            }

            is IdleAction.ClickHex -> {
                val updated = appState.copy(cursor = action.coords)
                trySelectUnit(updated)
            }

            is IdleAction.SelectUnit -> trySelectUnit(appState)
            is IdleAction.CycleUnit -> cycleUnit(appState)
            is IdleAction.CommitDeclarations -> commitDeclarations(appState)
        }
    }

    private fun trySelectUnit(appState: AppState): HandleResult {
        val unit = appState.gameState.unitAt(appState.cursor)
            ?: return HandleResult(appState)
        val turnState = appState.turnState

        if (turnState != null && appState.currentPhase == TurnPhase.MOVEMENT) {
            when (validateUnitSelection(unit, turnState)) {
                UnitSelectionResult.NOT_YOUR_UNIT ->
                    return HandleResult(appState, FlashMessage("Not your unit"))

                UnitSelectionResult.ALREADY_MOVED, UnitSelectionResult.ALREADY_ACTED ->
                    return HandleResult(appState, FlashMessage("Already moved"))

                UnitSelectionResult.VALID -> {}
            }
        }

        if (turnState != null && isAttackPhase(appState.currentPhase)) {
            when (validateAttackUnitSelection(unit, turnState)) {
                UnitSelectionResult.NOT_YOUR_UNIT ->
                    return HandleResult(appState, FlashMessage("Not your unit"))

                UnitSelectionResult.ALREADY_ACTED ->
                    return HandleResult(appState, FlashMessage("Already committed attacks"))

                UnitSelectionResult.ALREADY_MOVED, UnitSelectionResult.VALID -> {}
            }
        }

        val newPhase = when (appState.currentPhase) {
            TurnPhase.MOVEMENT -> manager.movementController.enter(unit, appState.gameState)
            TurnPhase.WEAPON_ATTACK -> manager.attackController.enter(unit, TurnPhase.WEAPON_ATTACK, appState.gameState)
            TurnPhase.PHYSICAL_ATTACK -> manager.attackController.enter(unit, TurnPhase.PHYSICAL_ATTACK, appState.gameState)
            else -> null
        }
        return if (newPhase != null) {
            HandleResult(appState.copy(phase = manager.wrap(newPhase)))
        } else {
            HandleResult(appState)
        }
    }

    private fun commitDeclarations(appState: AppState): HandleResult {
        val turnState = appState.turnState
        if (turnState == null || !isAttackPhase(appState.currentPhase)) {
            return HandleResult(appState)
        }

        if (!manager.attackController.canCommit()) {
            val declared = manager.attackController.declaredCount()
            val total = manager.attackController.currentImpulseUnitCount()
            return HandleResult(appState, FlashMessage("Declare all units first ($declared/$total declared)"))
        }

        val commitResult = manager.attackController.commitImpulse()
        val gameStateWithTorso = applyTorsoFacings(appState.gameState, commitResult.torsoFacings)

        var newTurnState: TurnState = turnState
        for (unitId in commitResult.unitIds) {
            newTurnState = advanceAfterUnitAttacked(newTurnState, unitId)
        }

        return if (newTurnState.allAttackImpulsesComplete) {
            if (appState.currentPhase == TurnPhase.WEAPON_ATTACK) {
                resolveWeaponAttacks(appState, gameStateWithTorso, newTurnState)
            } else {
                HandleResult(
                    appState.copy(
                        gameState = gameStateWithTorso,
                        currentPhase = nextPhase(appState.currentPhase),
                        phase = manager.idle(),
                        turnState = newTurnState,
                    )
                )
            }
        } else {
            manager.attackController.initializeImpulse(
                newTurnState.activeAttackPlayer,
                newTurnState.currentAttackImpulse.unitCount,
            )
            HandleResult(
                appState.copy(
                    gameState = gameStateWithTorso,
                    phase = manager.idle(buildAttackPrompt(newTurnState)),
                    turnState = newTurnState,
                )
            )
        }
    }

    private fun resolveWeaponAttacks(
        appState: AppState,
        gameStateWithTorso: battletech.tactical.model.GameState,
        newTurnState: TurnState,
    ): HandleResult {
        val declarations = manager.attackController.collectDeclarations()
        val (resolvedGameState, flash) = if (declarations.isNotEmpty()) {
            val (resolved, results) = resolveAttacks(declarations, gameStateWithTorso, random)
            val hitCount = results.count { it.hit }
            val totalDamage = results.sumOf { it.damageApplied }
            resolved to FlashMessage("Attacks resolved: ${results.size} attacks, $hitCount hits, $totalDamage damage")
        } else {
            gameStateWithTorso to null
        }
        manager.attackController.clearDeclarations()

        val physicalTurnState = newTurnState.copy(
            attackedUnitIds = emptySet(),
            currentAttackImpulseIndex = 0,
            unitsAttackedInCurrentImpulse = 0,
            attackOrder = emptyList(),
        )
        return HandleResult(
            appState.copy(
                gameState = resolvedGameState,
                currentPhase = nextPhase(TurnPhase.WEAPON_ATTACK),
                phase = manager.idle(),
                turnState = physicalTurnState,
            ),
            flash,
        )
    }

    private fun cycleUnit(appState: AppState): HandleResult {
        val turnState = appState.turnState
        val units = when {
            turnState != null && appState.currentPhase == TurnPhase.MOVEMENT ->
                selectableUnits(appState.gameState, turnState)

            turnState != null && isAttackPhase(appState.currentPhase) -> {
                val all = selectableAttackUnits(appState.gameState, turnState)
                val undeclared = all.filter { !manager.attackController.isDeclared(it.id) }
                undeclared.ifEmpty { all }
            }

            else -> appState.gameState.units
        }
        if (units.isEmpty()) return HandleResult(appState)

        val currentIdx = units.indexOfFirst { it.position == appState.cursor }
        val nextIdx = (currentIdx + 1) % units.size
        return HandleResult(appState.copy(cursor = units[nextIdx].position))
    }

    private fun isAttackPhase(phase: TurnPhase): Boolean =
        phase == TurnPhase.WEAPON_ATTACK || phase == TurnPhase.PHYSICAL_ATTACK

    private fun buildAttackPrompt(turnState: TurnState): String =
        attackPrompt(
            turnState,
            manager.attackController.declaredCount(),
            manager.attackController.currentImpulseUnitCount(),
        )
}
