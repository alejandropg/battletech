package battletech.tui.game

import battletech.tactical.action.UnitId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tui.input.BrowsingAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

public sealed interface MovementPhaseState : PhaseState {
    public val unitId: UnitId
    public val modes: List<ReachabilityMap>
    public val currentModeIndex: Int
    public val reachability: ReachabilityMap get() = modes[currentModeIndex]

    public data class Browsing(
        override val unitId: UnitId,
        override val modes: List<ReachabilityMap>,
        override val currentModeIndex: Int,
        val hoveredPath: List<HexCoordinates>?,
        val hoveredDestination: ReachableHex?,
    ) : MovementPhaseState {

        override fun processEvent(
            event: InputEvent,
            appState: AppState,
            phaseManager: PhaseManager,
        ): HandleResult? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapBrowsingEvent(event)
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                    ?.let { BrowsingAction.ClickHex(it) }
            } ?: return null

            val newCursor = when (action) {
                is BrowsingAction.MoveCursor -> moveCursor(appState.cursor, action.direction, appState.gameState.map)
                is BrowsingAction.ClickHex -> action.coords
                else -> appState.cursor
            }
            val updated = appState.copy(cursor = newCursor)
            val outcome = phaseManager.movementController.handle(action, this, newCursor, updated.gameState)
            return phaseManager.fromOutcome(outcome, updated)
        }
    }

    public data class SelectingFacing(
        override val unitId: UnitId,
        override val modes: List<ReachabilityMap>,
        override val currentModeIndex: Int,
        val hex: HexCoordinates,
        val options: List<ReachableHex>,
        val path: List<HexCoordinates>,
    ) : MovementPhaseState {

        override fun processEvent(
            event: InputEvent,
            appState: AppState,
            phaseManager: PhaseManager,
        ): HandleResult? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapFacingEvent(event)
                is MouseEvent -> return null
            } ?: return null

            val outcome = phaseManager.movementController.handle(action, this, appState.cursor, appState.gameState)
            return phaseManager.fromOutcome(outcome, appState)
        }
    }
}
