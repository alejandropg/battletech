package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.CombatUnit
import battletech.tactical.action.UnitId
import battletech.tactical.action.movement.MovementPreview
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tui.input.InputAction

public class MovementController(
    private val actionQueryService: ActionQueryService,
) {
    public companion object {
        public val FACING_ORDER: List<HexDirection> =
            listOf(HexDirection.N, HexDirection.NE, HexDirection.SE, HexDirection.S, HexDirection.SW, HexDirection.NW)
    }

    public fun enter(unit: CombatUnit, gameState: GameState): PhaseState.Movement.Browsing {
        val report = actionQueryService.getMovementActions(unit, gameState)
        val modes = report.actions
            .filterIsInstance<AvailableAction>()
            .mapNotNull { (it.preview as? MovementPreview)?.reachability }

        return PhaseState.Movement.Browsing(
            unitId = unit.id,
            modes = modes,
            currentModeIndex = 0,
            hoveredPath = null,
            hoveredDestination = null,
            prompt = modePrompt(unit.name, modes.firstOrNull()),
        )
    }

    public fun handle(
        action: InputAction,
        state: PhaseState.Movement,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (state) {
        is PhaseState.Movement.Browsing -> handleBrowsing(action, state, cursor, gameState)
        is PhaseState.Movement.SelectingFacing -> handleFacing(action, state, gameState)
    }

    private fun handleBrowsing(
        action: InputAction,
        state: PhaseState.Movement.Browsing,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is InputAction.Cancel -> PhaseOutcome.Cancelled
        is InputAction.Confirm -> handleBrowsingConfirm(state, gameState)
        is InputAction.SelectAction -> handleBrowsingSelectAction(action.index, state, gameState)
        is InputAction.MoveCursor, is InputAction.ClickHex -> {
            val updated = updatePathForCursor(cursor, state)
            PhaseOutcome.Continue(updated)
        }

        is InputAction.CycleUnit -> PhaseOutcome.Continue(cycleMode(state))
        else -> PhaseOutcome.Continue(state)
    }

    private fun handleFacing(
        action: InputAction,
        state: PhaseState.Movement.SelectingFacing,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is InputAction.Cancel -> {
            PhaseOutcome.Continue(
                PhaseState.Movement.Browsing(
                    unitId = state.unitId,
                    modes = state.modes,
                    currentModeIndex = state.currentModeIndex,
                    hoveredPath = null,
                    hoveredDestination = null,
                    prompt = modePrompt(null, state.reachability),
                ),
            )
        }
        is InputAction.SelectAction -> {
            val directionIndex = action.index - 1
            if (directionIndex !in FACING_ORDER.indices) return PhaseOutcome.Continue(state)
            val direction = FACING_ORDER[directionIndex]
            val destination = state.options.find { it.facing == direction }
                ?: return PhaseOutcome.Continue(state)
            PhaseOutcome.Complete(applyMovement(gameState, state.unitId, destination))
        }
        is InputAction.Confirm -> PhaseOutcome.Continue(state)
        else -> PhaseOutcome.Continue(state)
    }

    private fun handleBrowsingConfirm(
        state: PhaseState.Movement.Browsing,
        gameState: GameState,
    ): PhaseOutcome {
        val destination = state.hoveredDestination ?: return PhaseOutcome.Continue(state)
        val facingsAtHex = state.reachability.destinations
            .filter { it.position == destination.position }
        if (facingsAtHex.size > 1) {
            return PhaseOutcome.Continue(enterFacingSelection(state, destination.position, facingsAtHex))
        }
        return PhaseOutcome.Complete(applyMovement(gameState, state.unitId, destination))
    }

    private fun handleBrowsingSelectAction(
        index: Int,
        state: PhaseState.Movement.Browsing,
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

        return PhaseOutcome.Complete(applyMovement(gameState, state.unitId, destination))
    }

    private fun enterFacingSelection(
        state: PhaseState.Movement.Browsing,
        hex: HexCoordinates,
        facingsAtHex: List<ReachableHex>,
    ): PhaseState.Movement.SelectingFacing {
        val cheapest = facingsAtHex.minByOrNull { it.mpSpent }
        val path = cheapest?.path?.map { it.position } ?: emptyList()
        return PhaseState.Movement.SelectingFacing(
            unitId = state.unitId,
            modes = state.modes,
            currentModeIndex = state.currentModeIndex,
            hex = hex,
            options = facingsAtHex,
            path = path,
            prompt = "Select facing (1-6)",
        )
    }

    private fun updatePathForCursor(
        cursorPosition: HexCoordinates,
        state: PhaseState.Movement.Browsing,
    ): PhaseState.Movement.Browsing {
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

    private fun cycleMode(state: PhaseState.Movement.Browsing): PhaseState.Movement.Browsing {
        if (state.modes.isEmpty()) return state
        val nextIndex = (state.currentModeIndex + 1) % state.modes.size
        return PhaseState.Movement.Browsing(
            unitId = state.unitId,
            modes = state.modes,
            currentModeIndex = nextIndex,
            hoveredPath = null,
            hoveredDestination = null,
            prompt = modePrompt(null, state.modes[nextIndex]),
        )
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
        unitId: UnitId,
        destination: ReachableHex,
    ): GameState {
        val updatedUnits = gameState.units.map { unit ->
            if (unit.id == unitId) {
                unit.copy(position = destination.position, facing = destination.facing)
            } else {
                unit
            }
        }
        return gameState.copy(units = updatedUnits)
    }
}
