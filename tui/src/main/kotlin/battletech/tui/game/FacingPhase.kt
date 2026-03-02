package battletech.tui.game

import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

public class FacingPhase(
    private val manager: PhaseManager,
    override val state: PhaseState.Movement.SelectingFacing,
) : ActivePhase {

    override fun processEvent(event: InputEvent, appState: AppState): HandleResult? {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapFacingEvent(event)
            is MouseEvent -> return null
        } ?: return null

        val outcome = manager.movementController.handle(action, state, appState.cursor, appState.gameState)
        return manager.fromOutcome(outcome, appState)
    }
}
