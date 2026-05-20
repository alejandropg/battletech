package battletech.tui.game.phase

import battletech.tactical.model.CombatUnit
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.UnitId
import battletech.tactical.session.MoveUnit
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.movement.MovementMode
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tactical.query.PlayerView
import battletech.tui.game.AppState
import battletech.tui.game.FacingSelection
import battletech.tui.game.FlashMessage
import battletech.tui.game.RenderData
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

public sealed interface MovementPhase : Phase {
    override val turnPhase: TurnPhase get() = TurnPhase.MOVEMENT

    public data object SelectingUnit : MovementPhase {

        override fun handle(event: InputEvent, app: AppState): Transition? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapIdleEvent(event)
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                    ?.let { IdleAction.ClickHex(it) }
            } ?: return null

            return when (action) {
                is IdleAction.MoveCursor -> {
                    val newCursor = moveCursor(app.cursor, action.direction, app.gameState.map)
                    Transition(app.copy(cursor = newCursor))
                }

                is IdleAction.ClickHex -> trySelect(app.copy(cursor = action.coords))
                is IdleAction.SelectUnit -> trySelect(app)
                is IdleAction.CycleUnit -> cycleToNextUnit(app, app.gameState.unitAt(app.cursor)?.id)
                is IdleAction.CommitDeclarations -> Transition(app)
            }
        }

        override fun prompt(app: AppState): String {
            val turnState = app.turnState
            val playerName = if (turnState.activePlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
            val remaining = turnState.remainingInImpulse
            return "$playerName: select a unit to move ($remaining remaining)"
        }

        override fun selectedUnit(app: AppState): CombatUnit? = app.gameState.unitAt(app.cursor)

        override fun activePlayerLabel(app: AppState): String? {
            val turnState = app.turnState
            if (turnState.allImpulsesComplete) return null
            return if (turnState.activePlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
        }

        private fun trySelect(app: AppState): Transition {
            val unit = app.gameState.unitAt(app.cursor) ?: return Transition(app)
            val turnState = app.turnState

            if (unit.owner != turnState.activePlayer) {
                return Transition(app, FlashMessage("Not your unit"))
            }
            if (unit.id in turnState.movedUnitIds) {
                return Transition(app, FlashMessage("Already moved"))
            }

            val newPhase = enterBrowsing(unit, app.viewFor(unit.owner))
            return Transition(app.copy(phase = newPhase))
        }

    }

    public data class Browsing(
        val unitId: UnitId,
        val modes: List<ReachabilityMap>,
        val currentModeIndex: Int,
        val hoveredDestination: ReachableHex?,
    ) : MovementPhase {

        val reachability: ReachabilityMap get() = modes[currentModeIndex]

        val hoveredPath: List<HexCoordinates>?
            get() = hoveredDestination?.path?.map { it.position }

        private fun withCursorAt(cursor: HexCoordinates): Browsing {
            if (modes.isEmpty()) return this
            val cheapest = reachability.destinations
                .filter { it.position == cursor }
                .minByOrNull { it.mpSpent }
            return copy(hoveredDestination = cheapest)
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
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                    ?.let { BrowsingAction.ClickHex(it) }
            } ?: return null

            val newCursor = when (action) {
                is BrowsingAction.MoveCursor -> moveCursor(app.cursor, action.direction, app.gameState.map)
                is BrowsingAction.ClickHex -> action.coords
                else -> app.cursor
            }
            val updated = app.copy(cursor = newCursor)

            return when (action) {
                is BrowsingAction.Cancel -> Transition(updated.copy(phase = SelectingUnit))
                is BrowsingAction.ConfirmPath -> confirm(updated)
                is BrowsingAction.SelectFacing -> selectFacing(updated, action.index)
                is BrowsingAction.MoveCursor, is BrowsingAction.ClickHex ->
                    Transition(updated.copy(phase = withCursorAt(newCursor)))

                is BrowsingAction.CycleMode ->
                    Transition(updated.copy(phase = cycleMode()))

                is BrowsingAction.CycleUnit -> cycleToNextUnit(app, unitId)
            }
        }

        override fun prompt(app: AppState): String = modePrompt(reachability)

        override fun render(gameState: GameState): RenderData = RenderData(
            hexHighlights = reachabilityHighlights(reachability) + pathHighlights(hoveredPath),
            reachableFacings = reachability.facingsByPosition(),
        )

        override fun selectedUnit(app: AppState): CombatUnit? = app.gameState.unitById(unitId)

        override fun pathDestination(): HexCoordinates? = hoveredPath?.lastOrNull()

        override fun movementMode(): MovementMode = reachability.mode

        override fun activePlayerLabel(app: AppState): String? {
            val turnState = app.turnState
            return if (turnState.activePlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
        }

        private fun confirm(app: AppState): Transition {
            val destination = hoveredDestination ?: return Transition(app.copy(phase = this))
            val facingsAtHex = reachability.destinations.filter { it.position == destination.position }
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
            val facingsAtHex = reachability.destinations.filter { it.position == destination.position }
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
    ) : MovementPhase {

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
                is FacingAction.Cancel -> Transition(app.copy(phase = toBrowsing()))
                is FacingAction.SelectFacing -> commitByFacing(app, action.index)
                is FacingAction.CycleUnit -> cycleToNextUnit(app, unitId)
            }
        }

        override fun prompt(app: AppState): String = SELECT_FACING_PROMPT

        override fun render(gameState: GameState): RenderData = RenderData(
            hexHighlights = reachabilityHighlights(reachability) + pathHighlights(path),
            facingSelection = FacingSelection(hex, options.map { it.facing }.toSet()),
            reachableFacings = reachability.facingsByPosition(),
        )

        override fun selectedUnit(app: AppState): CombatUnit? = app.gameState.unitById(unitId)

        override fun pathDestination(): HexCoordinates? = path.lastOrNull()

        override fun movementMode(): MovementMode = reachability.mode

        override fun activePlayerLabel(app: AppState): String? {
            val turnState = app.turnState
            return if (turnState.activePlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
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

internal fun cycleToNextUnit(app: AppState, currentUnitId: UnitId?): Transition {
    val units = app.turnState.selectableUnits(app.gameState)
    if (units.isEmpty()) return Transition(app)
    val currentIdx = units.indexOfFirst { it.id == currentUnitId }
    val nextIdx = if (currentIdx == -1) 0 else (currentIdx + 1) % units.size
    val nextUnit = units[nextIdx]
    val nextPhase = enterBrowsing(nextUnit, app.viewFor(nextUnit.owner))
    return Transition(app.copy(cursor = nextUnit.position, phase = nextPhase))
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
    val unit = app.gameState.unitById(unitId) ?: return Transition(app)
    app.session.submitCommand(
        MoveUnit(
            playerId = unit.owner,
            unitId = unitId,
            destination = destination,
            mode = mode,
        ),
    )
    return Transition(app.copy(phase = battletech.tui.game.mapToTuiPhase(app.session.currentPhase)))
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
