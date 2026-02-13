package battletech.tui.game

import battletech.tui.input.InputAction
import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.action.movement.MovementPreview
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex

public class MovementPhaseController(
    private val actionQueryService: ActionQueryService,
) : PhaseController {

    override fun enter(unit: Unit, gameState: GameState): PhaseState {
        val report = actionQueryService.getMovementActions(unit, gameState)
        val reachability = report.actions
            .filterIsInstance<AvailableAction>()
            .mapNotNull { it.preview as? MovementPreview }
            .firstOrNull()
            ?.reachability

        return PhaseState(
            phase = TurnPhase.MOVEMENT,
            selectedUnitId = unit.id,
            reachability = reachability,
            prompt = "Select destination for ${unit.name}",
        )
    }

    override fun handleAction(
        action: InputAction,
        phaseState: PhaseState,
        gameState: GameState,
    ): PhaseControllerResult {
        return when (action) {
            is InputAction.Cancel -> PhaseControllerResult.Cancelled
            is InputAction.Confirm -> {
                val destination = phaseState.selectedDestination
                    ?: return PhaseControllerResult.UpdateState(phaseState)
                val updatedState = applyMovement(gameState, phaseState, destination)
                PhaseControllerResult.Complete(updatedState)
            }
            is InputAction.ClickHex -> {
                val reachability = phaseState.reachability
                    ?: return PhaseControllerResult.UpdateState(phaseState)
                val cheapest = findCheapestPath(action.coords, reachability)
                val path = cheapest?.path?.map { it.position }
                PhaseControllerResult.UpdateState(
                    phaseState.copy(
                        highlightedPath = path,
                        selectedDestination = cheapest,
                    ),
                )
            }
            is InputAction.MoveCursor -> PhaseControllerResult.UpdateState(phaseState)
            else -> PhaseControllerResult.UpdateState(phaseState)
        }
    }

    public fun updatePathForCursor(
        cursorPosition: HexCoordinates,
        phaseState: PhaseState,
    ): PhaseState {
        val reachability = phaseState.reachability ?: return phaseState
        val cheapest = findCheapestPath(cursorPosition, reachability)
        val path = cheapest?.path?.map { it.position }
        return phaseState.copy(
            highlightedPath = path,
            selectedDestination = cheapest,
        )
    }

    private fun findCheapestPath(
        position: HexCoordinates,
        reachability: ReachabilityMap,
    ): ReachableHex? {
        return reachability.destinations
            .filter { it.position == position }
            .minByOrNull { it.mpSpent }
    }

    private fun applyMovement(
        gameState: GameState,
        phaseState: PhaseState,
        destination: ReachableHex,
    ): GameState {
        val updatedUnits = gameState.units.map { unit ->
            if (unit.id == phaseState.selectedUnitId) {
                unit.copy(position = destination.position)
            } else {
                unit
            }
        }
        return gameState.copy(units = updatedUnits)
    }
}
