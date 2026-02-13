package battletech.tui.game

import battletech.tui.input.InputAction
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.model.GameState

public class HeatPhaseController : PhaseController {

    override fun enter(unit: Unit, gameState: GameState): PhaseState {
        return PhaseState(
            phase = TurnPhase.HEAT,
            selectedUnitId = unit.id,
            prompt = "Heat dissipation applied. Press Enter to continue.",
        )
    }

    override fun handleAction(
        action: InputAction,
        phaseState: PhaseState,
        gameState: GameState,
    ): PhaseControllerResult {
        return when (action) {
            is InputAction.Confirm -> {
                val updatedState = applyHeatDissipation(gameState, phaseState)
                PhaseControllerResult.Complete(updatedState)
            }
            is InputAction.Cancel -> PhaseControllerResult.Cancelled
            else -> PhaseControllerResult.UpdateState(phaseState)
        }
    }

    private fun applyHeatDissipation(gameState: GameState, phaseState: PhaseState): GameState {
        val updatedUnits = gameState.units.map { unit ->
            if (unit.id == phaseState.selectedUnitId) {
                val newHeat = maxOf(0, unit.currentHeat - unit.heatSinkCapacity)
                unit.copy(currentHeat = newHeat)
            } else {
                unit
            }
        }
        return gameState.copy(units = updatedUnits)
    }
}
