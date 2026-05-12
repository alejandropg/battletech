package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.CombatUnit
import battletech.tactical.action.UnitId
import battletech.tactical.action.movement.MovementPreview
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.movement.ReachableHex
import battletech.tui.input.BrowsingAction
import battletech.tui.input.FacingAction

public class MovementController(
    private val actionQueryService: ActionQueryService,
) {
    public companion object {
        public val FACING_ORDER: List<HexDirection> =
            listOf(HexDirection.N, HexDirection.NE, HexDirection.SE, HexDirection.S, HexDirection.SW, HexDirection.NW)
    }

    public fun enter(unit: CombatUnit, gameState: GameState): MovementPhaseState.Browsing {
        val report = actionQueryService.getMovementActions(unit, gameState)
        val modes = report.actions
            .filterIsInstance<AvailableAction>()
            .map { (it.preview as MovementPreview).reachability }

        return MovementPhaseState.Browsing(
            unitId = unit.id,
            modes = modes,
            currentModeIndex = 0,
            hoveredPath = null,
            hoveredDestination = null,
        )
    }

    public fun handle(
        action: BrowsingAction,
        state: MovementPhaseState.Browsing,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is BrowsingAction.Cancel -> PhaseOutcome.Cancelled
        is BrowsingAction.ConfirmPath -> handleBrowsingConfirm(state, gameState)
        is BrowsingAction.SelectFacing -> handleBrowsingSelectAction(action.index, state, gameState)
        is BrowsingAction.MoveCursor, is BrowsingAction.ClickHex -> {
            val updated = updatePathForCursor(cursor, state)
            PhaseOutcome.Continue(updated)
        }
        is BrowsingAction.CycleMode -> PhaseOutcome.Continue(cycleMode(state))
    }

    public fun handle(
        action: FacingAction,
        state: MovementPhaseState.SelectingFacing,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is FacingAction.Cancel -> {
            PhaseOutcome.Continue(
                MovementPhaseState.Browsing(
                    unitId = state.unitId,
                    modes = state.modes,
                    currentModeIndex = state.currentModeIndex,
                    hoveredPath = null,
                    hoveredDestination = null,
                ),
            )
        }
        is FacingAction.SelectFacing -> {
            val directionIndex = action.index - 1
            if (directionIndex !in FACING_ORDER.indices) return PhaseOutcome.Continue(state)
            val direction = FACING_ORDER[directionIndex]
            val destination = state.options.find { it.facing == direction }
                ?: return PhaseOutcome.Continue(state)
            PhaseOutcome.Complete(applyMovement(gameState, state.unitId, destination), state.unitId)
        }
    }

    private fun handleBrowsingConfirm(
        state: MovementPhaseState.Browsing,
        gameState: GameState,
    ): PhaseOutcome {
        val destination = state.hoveredDestination ?: return PhaseOutcome.Continue(state)
        val facingsAtHex = state.reachability.destinations
            .filter { it.position == destination.position }
        if (facingsAtHex.size > 1) {
            return PhaseOutcome.Continue(enterFacingSelection(state, destination.position, facingsAtHex))
        }
        return PhaseOutcome.Complete(applyMovement(gameState, state.unitId, destination), state.unitId)
    }

    private fun handleBrowsingSelectAction(
        index: Int,
        state: MovementPhaseState.Browsing,
        gameState: GameState,
    ): PhaseOutcome {
        val directionIndex = index - 1
        if (directionIndex !in FACING_ORDER.indices) return PhaseOutcome.Continue(state)
        val direction = FACING_ORDER[directionIndex]

        val selected = state.hoveredDestination
            ?: return PhaseOutcome.Continue(state)
        val facingsAtHex = state.reachability.destinations
            .filter { it.position == selected.position }
        val destination = facingsAtHex.find { it.facing == direction }
            ?: return PhaseOutcome.Continue(state)

        return PhaseOutcome.Complete(applyMovement(gameState, state.unitId, destination), state.unitId)
    }

    private fun enterFacingSelection(
        state: MovementPhaseState.Browsing,
        hex: HexCoordinates,
        facingsAtHex: List<ReachableHex>,
    ): MovementPhaseState.SelectingFacing {
        val cheapest = facingsAtHex.minByOrNull { it.mpSpent }
        val path = cheapest?.path?.map { it.position } ?: emptyList()
        return MovementPhaseState.SelectingFacing(
            unitId = state.unitId,
            modes = state.modes,
            currentModeIndex = state.currentModeIndex,
            hex = hex,
            options = facingsAtHex,
            path = path,
        )
    }

    private fun updatePathForCursor(
        cursorPosition: HexCoordinates,
        state: MovementPhaseState.Browsing,
    ): MovementPhaseState.Browsing {
        if (state.modes.isEmpty()) return state
        val reachability = state.reachability
        val cheapest = reachability.destinations
            .filter { it.position == cursorPosition }
            .minByOrNull { it.mpSpent }
        val path = cheapest?.path?.map { it.position }
        return state.copy(
            hoveredPath = path,
            hoveredDestination = cheapest,
        )
    }

    private fun cycleMode(state: MovementPhaseState.Browsing): MovementPhaseState.Browsing {
        if (state.modes.isEmpty()) return state
        val nextIndex = (state.currentModeIndex + 1) % state.modes.size
        return MovementPhaseState.Browsing(
            unitId = state.unitId,
            modes = state.modes,
            currentModeIndex = nextIndex,
            hoveredPath = null,
            hoveredDestination = null,
        )
    }

    private fun applyMovement(
        gameState: GameState,
        unitId: UnitId,
        destination: ReachableHex,
    ): GameState {
        val updatedUnits = gameState.units.map { unit ->
            if (unit.id == unitId) {
                unit.copy(position = destination.position, facing = destination.facing, torsoFacing = destination.facing)
            } else {
                unit
            }
        }
        return gameState.copy(units = updatedUnits)
    }
}
