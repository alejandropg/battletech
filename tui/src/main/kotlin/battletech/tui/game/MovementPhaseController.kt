package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.action.movement.MovementPreview
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tui.input.InputAction

public class MovementPhaseController(
    private val actionQueryService: ActionQueryService,
) : PhaseController {

    public companion object {
        public val FACING_ORDER: List<HexDirection> =
            listOf(HexDirection.N, HexDirection.NE, HexDirection.SE, HexDirection.S, HexDirection.SW, HexDirection.NW)
    }

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
            facingSelectionHex = null,
            facingOptions = emptyList(),
            prompt = modePrompt(unitName, nextReachability),
        )
    }

    override fun handleAction(
        action: InputAction,
        phaseState: PhaseState,
        gameState: GameState,
    ): PhaseControllerResult {
        return when (action) {
            is InputAction.Cancel -> {
                if (phaseState.facingSelectionHex != null) {
                    PhaseControllerResult.UpdateState(
                        phaseState.copy(
                            facingSelectionHex = null,
                            facingOptions = emptyList(),
                        ),
                    )
                } else {
                    PhaseControllerResult.Cancelled
                }
            }
            is InputAction.Confirm -> {
                if (phaseState.facingSelectionHex != null) {
                    return PhaseControllerResult.UpdateState(phaseState)
                }
                val destination = phaseState.selectedDestination
                    ?: return PhaseControllerResult.UpdateState(phaseState)
                val facingsAtHex = phaseState.reachability?.destinations
                    ?.filter { it.position == destination.position } ?: emptyList()
                if (facingsAtHex.size > 1) {
                    val cheapest = facingsAtHex.minByOrNull { it.mpSpent }
                    val path = cheapest?.path?.map { it.position }
                    return PhaseControllerResult.UpdateState(
                        phaseState.copy(
                            highlightedPath = path,
                            selectedDestination = null,
                            facingSelectionHex = destination.position,
                            facingOptions = facingsAtHex,
                        ),
                    )
                }
                PhaseControllerResult.Complete(applyMovement(gameState, phaseState, destination))
            }
            is InputAction.ClickHex -> handleClickHex(action.coords, phaseState, gameState)
            is InputAction.SelectAction -> handleSelectAction(action.index, phaseState, gameState)
            is InputAction.MoveCursor -> PhaseControllerResult.UpdateState(phaseState)
            else -> PhaseControllerResult.UpdateState(phaseState)
        }
    }

    private fun handleClickHex(
        coords: HexCoordinates,
        phaseState: PhaseState,
        gameState: GameState,
    ): PhaseControllerResult {
        val reachability = phaseState.reachability
            ?: return PhaseControllerResult.UpdateState(phaseState)

        val facingsAtHex = reachability.destinations.filter { it.position == coords }

        return when (facingsAtHex.size) {
            0 -> PhaseControllerResult.UpdateState(
                phaseState.copy(
                    highlightedPath = null,
                    selectedDestination = null,
                    facingSelectionHex = null,
                    facingOptions = emptyList(),
                ),
            )
            1 -> {
                val destination = facingsAtHex.first()
                PhaseControllerResult.Complete(applyMovement(gameState, phaseState, destination))
            }
            else -> {
                // Multiple facings available — enter facing selection sub-state
                val cheapest = facingsAtHex.minByOrNull { it.mpSpent }
                val path = cheapest?.path?.map { it.position }
                PhaseControllerResult.UpdateState(
                    phaseState.copy(
                        highlightedPath = path,
                        selectedDestination = null,
                        facingSelectionHex = coords,
                        facingOptions = facingsAtHex,
                    ),
                )
            }
        }
    }

    private fun handleSelectAction(
        index: Int,
        phaseState: PhaseState,
        gameState: GameState,
    ): PhaseControllerResult {
        val directionIndex = index - 1
        if (directionIndex !in FACING_ORDER.indices) return PhaseControllerResult.UpdateState(phaseState)
        val direction = FACING_ORDER[directionIndex]

        // Already in facing selection sub-state (existing behavior)
        if (phaseState.facingSelectionHex != null) {
            val destination = phaseState.facingOptions.find { it.facing == direction }
                ?: return PhaseControllerResult.UpdateState(phaseState)
            return PhaseControllerResult.Complete(applyMovement(gameState, phaseState, destination))
        }

        // Direct facing selection from movement browsing (new shortcut)
        val selected = phaseState.selectedDestination ?: return PhaseControllerResult.UpdateState(phaseState)
        val facingsAtHex = phaseState.reachability?.destinations
            ?.filter { it.position == selected.position } ?: emptyList()
        if (facingsAtHex.size <= 1) return PhaseControllerResult.UpdateState(phaseState)
        val destination = facingsAtHex.find { it.facing == direction }
            ?: return PhaseControllerResult.UpdateState(phaseState)
        return PhaseControllerResult.Complete(applyMovement(gameState, phaseState, destination))
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
            facingSelectionHex = null,
            facingOptions = emptyList(),
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
