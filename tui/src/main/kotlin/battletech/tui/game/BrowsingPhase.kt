package battletech.tui.game

import battletech.tui.input.BrowsingAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

public class BrowsingPhase(
    private val manager: PhaseManager,
    override val state: PhaseState.Movement.Browsing,
) : ActivePhase {

    override fun processEvent(event: InputEvent, appState: AppState): HandleResult? {
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
        val outcome = manager.movementController.handle(action, state, newCursor, updated.gameState)
        return manager.fromOutcome(outcome, updated)
    }
}
