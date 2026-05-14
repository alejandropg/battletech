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
        is BrowsingAction.ConfirmPath -> confirm(state, gameState)
        is BrowsingAction.SelectFacing -> selectFacing(state, action.index, gameState)
        is BrowsingAction.MoveCursor, is BrowsingAction.ClickHex ->
            PhaseOutcome.Continue(state.withCursorAt(cursor))
        is BrowsingAction.CycleMode -> PhaseOutcome.Continue(state.cycleMode())
    }

    public fun handle(
        action: FacingAction,
        state: MovementPhaseState.SelectingFacing,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is FacingAction.Cancel -> PhaseOutcome.Continue(state.toBrowsing())
        is FacingAction.SelectFacing ->
            commitByFacing(state.options, action.index, state.unitId, gameState)
                ?: PhaseOutcome.Continue(state)
    }

    private fun confirm(state: MovementPhaseState.Browsing, gameState: GameState): PhaseOutcome {
        val destination = state.hoveredDestination ?: return PhaseOutcome.Continue(state)
        val facingsAtHex = state.reachability.destinations.filter { it.position == destination.position }
        return if (facingsAtHex.size > 1) {
            PhaseOutcome.Continue(
                MovementPhaseState.SelectingFacing(
                    unitId = state.unitId,
                    modes = state.modes,
                    currentModeIndex = state.currentModeIndex,
                    hex = destination.position,
                    options = facingsAtHex,
                ),
            )
        } else {
            commit(destination, state.unitId, gameState)
        }
    }

    private fun selectFacing(
        state: MovementPhaseState.Browsing,
        index: Int,
        gameState: GameState,
    ): PhaseOutcome {
        val destination = state.hoveredDestination ?: return PhaseOutcome.Continue(state)
        val facingsAtHex = state.reachability.destinations.filter { it.position == destination.position }
        return commitByFacing(facingsAtHex, index, state.unitId, gameState) ?: PhaseOutcome.Continue(state)
    }

    private fun commitByFacing(
        options: List<ReachableHex>,
        index: Int,
        unitId: UnitId,
        gameState: GameState,
    ): PhaseOutcome? {
        val direction = FACING_ORDER.getOrNull(index - 1) ?: return null
        val destination = options.find { it.facing == direction } ?: return null
        return commit(destination, unitId, gameState)
    }

    private fun commit(
        destination: ReachableHex,
        unitId: UnitId,
        gameState: GameState,
    ): PhaseOutcome = PhaseOutcome.Complete(gameState.moveUnit(unitId, destination), unitId)
}
