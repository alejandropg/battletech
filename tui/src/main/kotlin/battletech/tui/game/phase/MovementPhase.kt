package battletech.tui.game.phase

import battletech.tactical.heat.movementHeatSources
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.TurnPhase
import battletech.tactical.movement.MovementRules
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tactical.movement.hexesMoved
import battletech.tactical.query.PlayerView
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.StandUp
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.VisibleUnit
import battletech.tui.game.AppState
import battletech.tui.game.FacingSelection
import battletech.tui.game.FlashMessage
import battletech.tui.game.RenderData
import battletech.tui.game.displayName
import battletech.tui.game.mapToTuiPhase
import battletech.tui.game.moveCursor
import battletech.tui.game.pathHighlights
import battletech.tui.game.reachabilityHighlights
import battletech.tui.input.BrowsingAction
import battletech.tui.input.FacingAction
import battletech.tui.input.IdleAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

internal val FACING_ORDER: List<HexDirection> = listOf(
    HexDirection.N, HexDirection.NE, HexDirection.SE,
    HexDirection.S, HexDirection.SW, HexDirection.NW,
)

internal const val SELECT_FACING_PROMPT = "Select facing (1-6)"

internal sealed interface MovementPhase : Phase {
    override val turnPhase: TurnPhase get() = TurnPhase.MOVEMENT

    data object SelectingUnit : MovementPhase {

        override fun handle(event: InputEvent, app: AppState): Transition? {
            val turnState = app.turnState
            // Host mode renders (and accepts input) before the session's advance() kickstart
            // fires — the movement impulse sequence is empty until a client joins. Every other
            // field this phase touches (activePlayer, selectableUnits) indexes into that
            // sequence and would throw, so only cursor movement is safe here.
            if (turnState.movement.isComplete) {
                val action = mapIdleInput(event) ?: return null
                return if (action is IdleAction.MoveCursor) handleCursorMove(app, action) else Transition(app)
            }
            return handleUnitSelection(
                event = event,
                app = app,
                activePlayer = { turnState.movement.activePlayer },
                selectableUnits = { turnState.selectableUnits(app.visibleState.units) },
                selectGuard = { unit ->
                    if (unit.id in turnState.movement.movedUnitIds) FlashMessage("Already moved") else null
                },
                enterFor = ::enterMovementSubMode,
            )
        }

        override fun prompt(app: AppState): String {
            val turnState = app.turnState
            // Host mode renders before the session's advance() kickstart fires (it waits for
            // the opponent to join), so the movement impulse sequence can still be empty here —
            // isComplete is true in that case (0 >= 0), same as a normally-finished sequence.
            if (turnState.movement.isComplete) return "Waiting for game to start…"
            val playerName = turnState.movement.activePlayer.displayName
            val remaining = turnState.movement.remainingInImpulse
            return "$playerName: select a unit to move ($remaining remaining)"
        }

        override fun selectedUnit(app: AppState): VisibleUnit? = app.visibleState.units.at(app.cursor)

        override fun unitStatus(app: AppState): VisibleUnit? = cursorUnitStatus(app)

        override fun activePlayerLabel(app: AppState): String? {
            val turnState = app.turnState
            if (turnState.movement.isComplete) return null
            return turnState.movement.activePlayer.displayName
        }

    }

    public data class Browsing(
        val unitId: UnitId,
        val modes: List<ReachabilityMap>,
        val currentModeIndex: Int,
        val hoveredDestination: ReachableHex?,
    ) : MovementPhase, CancelableSubPhase {

        val reachability: ReachabilityMap get() = modes[currentModeIndex]

        val hoveredPath: List<HexCoordinates>?
            get() = hoveredDestination?.path?.map { it.position }

        internal fun withCursorAt(cursor: HexCoordinates, app: AppState): Browsing {
            if (modes.isEmpty()) return this
            val cheapest = destinationsAt(cursor, app).minByOrNull { it.mpSpent }
            return copy(hoveredDestination = cheapest)
        }

        /**
         * The reachable hexes at [cursor] for the current mode, plus — when [cursor] is the
         * unit's own hex — the zero-cost "stay put" option. [ReachabilityCalculator]
         * deliberately excludes the unit's current position+facing from `destinations` (it
         * only ever proposes hexes reached by actually moving), so without this the TUI has
         * no way to offer "keep current facing" as a destination. [MovementRules.stationaryHex]
         * is the same server-authoritative shortcut `MoveUnit` already accepts, so merging it
         * in here just exposes an option the server already supports.
         */
        private fun destinationsAt(cursor: HexCoordinates, app: AppState): List<ReachableHex> {
            val fromReachability = reachability.destinations.filter { it.position == cursor }
            val unit = app.ownUnit(unitId)
            return if (cursor == unit.position) {
                fromReachability + MovementRules.stationaryHex(unit)
            } else {
                fromReachability
            }
        }

        private fun cycleMode(): Browsing {
            if (modes.isEmpty()) return this
            return copy(
                currentModeIndex = (currentModeIndex + 1) % modes.size,
                hoveredDestination = null,
            )
        }

        override fun handle(event: InputEvent, app: AppState): Transition? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapBrowsingEvent(event)
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = BOARD_ORIGIN_X, boardY = BOARD_ORIGIN_Y)
                    ?.let { BrowsingAction.ClickHex(it) }
            } ?: return null

            val newCursor = when (action) {
                is BrowsingAction.MoveCursor -> moveCursor(app.cursor, action.direction, app.visibleState.map)
                is BrowsingAction.ClickHex -> action.coords
                else -> app.cursor
            }
            val updated = app.copy(cursor = newCursor)

            return when (action) {
                is BrowsingAction.Cancel -> onCancel(updated)
                is BrowsingAction.ConfirmPath -> confirm(updated)
                is BrowsingAction.SelectFacing -> selectFacing(updated, action.index)
                is BrowsingAction.MoveCursor, is BrowsingAction.ClickHex ->
                    Transition(updated.copy(phase = withCursorAt(newCursor, updated)))

                is BrowsingAction.CycleMode ->
                    Transition(updated.copy(phase = cycleMode().withCursorAt(newCursor, updated)))

                is BrowsingAction.CycleUnit -> cycleToNextUnit(app, unitId)
            }
        }

        override fun prompt(app: AppState): String = modePrompt(reachability)

        override fun render(app: AppState): RenderData = RenderData(
            hexHighlights = reachabilityHighlights(reachability) + pathHighlights(hoveredPath),
            reachableFacings = reachability.facingsByPosition(),
        )

        override fun selectedUnit(app: AppState): VisibleUnit? = app.visibleState.units.byId(unitId)

        override fun onCancel(app: AppState): Transition = Transition(app.copy(phase = SelectingUnit))

        override fun pendingHeat(app: AppState): List<HeatSource> {
            val destination = hoveredDestination ?: return emptyList()
            val position = app.visibleState.units.byId(unitId).position
            val hexes = hexesMoved(position, destination)
            return movementHeatSources(reachability.mode, hexes)
        }

        override fun pathDestination(): HexCoordinates? = hoveredPath?.lastOrNull()

        override fun movementMode(): MovementMode = reachability.mode

        override fun activePlayerLabel(app: AppState): String? {
            val turnState = app.turnState
            return turnState.movement.activePlayer.displayName
        }

        private fun confirm(app: AppState): Transition {
            val destination = hoveredDestination ?: return Transition(app.copy(phase = this))
            val facingsAtHex = destinationsAt(destination.position, app)
            return if (facingsAtHex.size > 1) {
                Transition(
                    app.copy(
                        phase = SelectingFacing(
                            unitId = unitId,
                            modes = modes,
                            currentModeIndex = currentModeIndex,
                            hex = destination.position,
                            options = facingsAtHex,
                        ),
                    ),
                )
            } else {
                commitMove(app, destination)
            }
        }

        private fun selectFacing(app: AppState, index: Int): Transition {
            val destination = hoveredDestination ?: return Transition(app.copy(phase = this))
            val facingsAtHex = destinationsAt(destination.position, app)
            val direction = FACING_ORDER.getOrNull(index - 1) ?: return Transition(app.copy(phase = this))
            val choice = facingsAtHex.find { it.facing == direction }
                ?: return Transition(app.copy(phase = this))
            return commitMove(app, choice)
        }

        private fun commitMove(app: AppState, destination: ReachableHex): Transition =
            submitMove(app, unitId, destination, reachability.mode)
    }

    public data class SelectingFacing(
        val unitId: UnitId,
        val modes: List<ReachabilityMap>,
        val currentModeIndex: Int,
        val hex: HexCoordinates,
        val options: List<ReachableHex>,
    ) : MovementPhase, CancelableSubPhase {

        val reachability: ReachabilityMap get() = modes[currentModeIndex]

        val path: List<HexCoordinates>
            get() = options.minByOrNull { it.mpSpent }
                ?.path
                ?.map { it.position }
                ?: emptyList()

        public fun toBrowsing(): Browsing = Browsing(
            unitId = unitId,
            modes = modes,
            currentModeIndex = currentModeIndex,
            hoveredDestination = null,
        )

        override fun handle(event: InputEvent, app: AppState): Transition? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapFacingEvent(event)
                is MouseEvent -> return null
            } ?: return null

            return when (action) {
                is FacingAction.Cancel -> onCancel(app)
                is FacingAction.SelectFacing -> commitByFacing(app, action.index)
                is FacingAction.CycleUnit -> cycleToNextUnit(app, unitId)
            }
        }

        override fun prompt(app: AppState): String = SELECT_FACING_PROMPT

        override fun render(app: AppState): RenderData = RenderData(
            hexHighlights = reachabilityHighlights(reachability) + pathHighlights(path),
            facingSelection = FacingSelection(hex, options.map { it.facing }.toSet()),
            reachableFacings = reachability.facingsByPosition(),
        )

        override fun selectedUnit(app: AppState): VisibleUnit = app.visibleState.units.byId(unitId)

        override fun onCancel(app: AppState): Transition =
            Transition(app.copy(phase = toBrowsing().withCursorAt(app.cursor, app)))

        override fun pendingHeat(app: AppState): List<HeatSource> {
            val position = app.visibleState.units.byId(unitId).position
            val destination = options.minByOrNull { it.mpSpent } ?: return emptyList()
            val hexes = hexesMoved(position, destination)
            return movementHeatSources(reachability.mode, hexes)
        }

        override fun pathDestination(): HexCoordinates? = path.lastOrNull()

        override fun movementMode(): MovementMode = reachability.mode

        override fun activePlayerLabel(app: AppState): String? {
            val turnState = app.turnState
            return turnState.movement.activePlayer.displayName
        }

        private fun commitByFacing(app: AppState, index: Int): Transition {
            val direction = FACING_ORDER.getOrNull(index - 1) ?: return Transition(app.copy(phase = this))
            val destination = options.find { it.facing == direction }
                ?: return Transition(app.copy(phase = this))
            return submitMove(app, unitId, destination, reachability.mode)
        }
    }
}

internal fun enterBrowsing(unit: CombatUnit, view: PlayerView): MovementPhase.Browsing {
    val modes = view.legalMovementsFor(unit.id)
    return MovementPhase.Browsing(
        unitId = unit.id,
        modes = modes,
        currentModeIndex = 0,
        hoveredDestination = null,
    )
}

/**
 * Enter the movement sub-mode for [unit]: a prone unit must stand first (which
 * re-syncs to whatever phase the session settles in), otherwise browse legal
 * destinations. Shared by the idle Tab/Enter selection ([handleUnitSelection])
 * and the in-sub-mode cycle ([cycleToNextUnit]) so all movement selections
 * treat prone units identically.
 */
internal fun enterMovementSubMode(unit: CombatUnit, app: AppState): Transition =
    if (unit.isProne) {
        // A prone unit must stand before it can move.
        val result = app.submitCommand(StandUp(playerId = unit.owner, unitId = unit.id))
        Transition(app.copy(phase = mapToTuiPhase(app.anySession.currentPhase)), flash = rejectionFlash(result))
    } else {
        val browsing = enterBrowsing(unit, app.viewFor(unit.owner))
        Transition(app.copy(phase = browsing.withCursorAt(app.cursor, app)))
    }

internal fun cycleToNextUnit(app: AppState, currentUnitId: UnitId?): Transition {
    val units = app.turnState.selectableUnits(app.visibleState.units)
    if (units.isEmpty()) return Transition(app)
    val currentIdx = units.indexOfFirst { it.id == currentUnitId }
    val nextIdx = if (currentIdx == -1) 0 else (currentIdx + 1) % units.size
    val nextUnit = units[nextIdx]
    return enterMovementSubMode(app.ownUnit(nextUnit.id), app.copy(cursor = nextUnit.position))
}

/**
 * Submit the MoveUnit command to the session and re-sync the TUI phase to
 * whatever phase the session settles in after the cascade. After the last
 * movement impulse the session ends up in WEAPON_ATTACK (or, in tests with
 * no enemies, may cascade further).
 */
private fun submitMove(
    app: AppState,
    unitId: UnitId,
    destination: ReachableHex,
    mode: MovementMode,
): Transition {
    val owner = app.visibleState.units.byId(unitId).owner
    val result = app.submitCommand(
        MoveUnit(
            playerId = owner,
            unitId = unitId,
            destination = destination,
            mode = mode,
        ),
    )
    return Transition(app.copy(phase = mapToTuiPhase(app.anySession.currentPhase)), flash = rejectionFlash(result))
}

internal fun modePrompt(reachability: ReachabilityMap?): String {
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
    return "$modeName (${reachability.maxMP} MP)$suffix"
}
