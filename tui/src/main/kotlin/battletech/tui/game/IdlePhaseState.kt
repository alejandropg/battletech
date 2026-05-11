package battletech.tui.game

import battletech.tactical.action.TurnPhase
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

                UnitSelectionResult.ALREADY_MOVED ->
                    return HandleResult(appState, FlashMessage("Already moved"))

                UnitSelectionResult.VALID -> {}
            }
        }

        if (turnState != null && isAttackPhase(appState.currentPhase)) {
            when (validateAttackUnitSelection(unit, turnState)) {
                UnitSelectionResult.NOT_YOUR_UNIT ->
                    return HandleResult(appState, FlashMessage("Not your unit"))

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

    private fun commitDeclarations(appState: AppState, phaseManager: PhaseManager): HandleResult =
        phaseManager.commitAttackImpulse(appState)

    private fun cycleUnit(appState: AppState, phaseManager: PhaseManager): HandleResult {
        val turnState = appState.turnState
        val units = when {
            turnState != null && appState.currentPhase == TurnPhase.MOVEMENT ->
                selectableUnits(appState.gameState, turnState)

            turnState != null && isAttackPhase(appState.currentPhase) ->
                selectableAttackUnits(appState.gameState, turnState)

            else -> appState.gameState.units
        }
        if (units.isEmpty()) return HandleResult(appState)

        val currentIdx = units.indexOfFirst { it.position == appState.cursor }
        val nextIdx = (currentIdx + 1) % units.size
        return HandleResult(appState.copy(cursor = units[nextIdx].position))
    }
}
