package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.resolveAttacks
import battletech.tactical.model.GameState
import battletech.tui.input.IdleAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

public data class IdlePhaseState(
    override val prompt: String = "Move cursor to select a unit",
) : PhaseState {

    override fun processEvent(
        event: InputEvent,
        appState: AppState,
        phaseManager: PhaseManager,
    ): HandleResult? {
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
                trySelectUnit(updated, phaseManager)
            }

            is IdleAction.SelectUnit -> trySelectUnit(appState, phaseManager)
            is IdleAction.CycleUnit -> cycleUnit(appState, phaseManager)
            is IdleAction.CommitDeclarations -> commitDeclarations(appState, phaseManager)
        }
    }

    private fun trySelectUnit(appState: AppState, phaseManager: PhaseManager): HandleResult {
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
            TurnPhase.MOVEMENT -> phaseManager.movementController.enter(unit, appState.gameState)
            TurnPhase.WEAPON_ATTACK -> phaseManager.attackController.enter(unit, TurnPhase.WEAPON_ATTACK, appState.gameState)
            TurnPhase.PHYSICAL_ATTACK -> phaseManager.attackController.enter(unit, TurnPhase.PHYSICAL_ATTACK, appState.gameState)
            else -> null
        }
        return if (newPhase != null) {
            HandleResult(appState.copy(phase = newPhase))
        } else {
            HandleResult(appState)
        }
    }

    private fun commitDeclarations(appState: AppState, phaseManager: PhaseManager): HandleResult {
        val turnState = appState.turnState
        if (turnState == null || !isAttackPhase(appState.currentPhase)) {
            return HandleResult(appState)
        }

        if (!phaseManager.attackController.canCommit()) {
            val declared = phaseManager.attackController.declaredCount()
            val total = phaseManager.attackController.currentImpulseUnitCount()
            return HandleResult(appState, FlashMessage("Declare all units first ($declared/$total declared)"))
        }

        val commitResult = phaseManager.attackController.commitImpulse()
        val gameStateWithTorso = applyTorsoFacings(appState.gameState, commitResult.torsoFacings)

        var newTurnState: TurnState = turnState
        for (unitId in commitResult.unitIds) {
            newTurnState = advanceAfterUnitAttacked(newTurnState, unitId)
        }

        return if (newTurnState.allAttackImpulsesComplete) {
            if (appState.currentPhase == TurnPhase.WEAPON_ATTACK) {
                resolveWeaponAttacks(appState, gameStateWithTorso, newTurnState, phaseManager)
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
            phaseManager.attackController.initializeImpulse(
                newTurnState.activeAttackPlayer,
                newTurnState.currentAttackImpulse.unitCount,
            )
            HandleResult(
                appState.copy(
                    gameState = gameStateWithTorso,
                    phase = IdlePhaseState(buildAttackPrompt(newTurnState, phaseManager)),
                    turnState = newTurnState,
                )
            )
        }
    }

    private fun resolveWeaponAttacks(
        appState: AppState,
        gameStateWithTorso: GameState,
        newTurnState: TurnState,
        phaseManager: PhaseManager,
    ): HandleResult {
        val declarations = phaseManager.attackController.collectDeclarations()
        val (resolvedGameState, flash) = if (declarations.isNotEmpty()) {
            val (resolved, results) = resolveAttacks(declarations, gameStateWithTorso, phaseManager.random)
            val hitCount = results.count { it.hit }
            val totalDamage = results.sumOf { it.damageApplied }
            resolved to FlashMessage("Attacks resolved: ${results.size} attacks, $hitCount hits, $totalDamage damage")
        } else {
            gameStateWithTorso to null
        }
        phaseManager.attackController.clearDeclarations()

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
                phase = IdlePhaseState(),
                turnState = physicalTurnState,
            ),
            flash,
        )
    }

    private fun cycleUnit(appState: AppState, phaseManager: PhaseManager): HandleResult {
        val turnState = appState.turnState
        val units = when {
            turnState != null && appState.currentPhase == TurnPhase.MOVEMENT ->
                selectableUnits(appState.gameState, turnState)

            turnState != null && isAttackPhase(appState.currentPhase) -> {
                val all = selectableAttackUnits(appState.gameState, turnState)
                val undeclared = all.filter { !phaseManager.attackController.isDeclared(it.id) }
                undeclared.ifEmpty { all }
            }

            else -> appState.gameState.units
        }
        if (units.isEmpty()) return HandleResult(appState)

        val currentIdx = units.indexOfFirst { it.position == appState.cursor }
        val nextIdx = (currentIdx + 1) % units.size
        return HandleResult(appState.copy(cursor = units[nextIdx].position))
    }
}
