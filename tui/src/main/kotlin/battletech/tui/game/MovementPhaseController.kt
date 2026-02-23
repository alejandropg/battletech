package battletech.tui.game

import battletech.tui.input.InputAction
import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.action.movement.MovementPreview
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex

public class MovementPhaseController(
    private val actionQueryService: ActionQueryService,
) : PhaseController {

    override fun enter(unit: Unit, gameState: GameState): PhaseState {
        val report = actionQueryService.getMovementActions(unit, gameState)
        val availableModes = report.actions
            .filterIsInstance<AvailableAction>()
            .mapNotNull { (it.preview as? MovementPreview)?.reachability }

        val reachability = availableModes.firstOrNull()

        return PhaseState(
            phase = TurnPhase.MOVEMENT,
            selectedUnitId = unit.id,
            reachability = reachability,
            availableModes = availableModes,
            currentModeIndex = 0,
            prompt = modePrompt(unit.name, reachability),
        )
    }

    public fun cycleMode(phaseState: PhaseState, unitName: String): PhaseState {
        if (phaseState.availableModes.isEmpty()) return phaseState
        val nextIndex = (phaseState.currentModeIndex + 1) % phaseState.availableModes.size
        val nextReachability = phaseState.availableModes[nextIndex]
        return phaseState.copy(
            currentModeIndex = nextIndex,
            reachability = nextReachability,
            highlightedPath = null,
            selectedDestination = null,
            prompt = modePrompt(unitName, nextReachability),
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

    private fun modePrompt(unitName: String?, reachability: ReachabilityMap?): String {
        if (reachability == null) return "No movement available"
        val modeName = when (reachability.mode) {
            MovementMode.WALK -> "Walk"
            MovementMode.RUN -> "Run"
            MovementMode.JUMP -> "Jump"
        }
        val suffix = when (reachability.mode) {
            MovementMode.RUN -> " (+2 to-hit)"
            MovementMode.JUMP -> " (+3 to-hit)"
            else -> ""
        }
        val name = if (unitName != null) " $unitName" else ""
        return "$modeName$name (${reachability.maxMP} MP)$suffix"
    }

    private fun applyMovement(
        gameState: GameState,
        phaseState: PhaseState,
        destination: ReachableHex,
    ): GameState {
        val updatedUnits = gameState.units.map { unit ->
            if (unit.id == phaseState.selectedUnitId) {
                unit.copy(position = destination.position, facing = destination.facing)
            } else {
                unit
            }
        }
        return gameState.copy(units = updatedUnits)
    }
}
