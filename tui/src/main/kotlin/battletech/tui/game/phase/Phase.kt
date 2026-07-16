package battletech.tui.game.phase

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.VisibleUnit
import battletech.tui.game.AppState
import battletech.tui.game.PanelId
import battletech.tui.game.RenderData
import com.github.ajalt.mordant.input.InputEvent

/**
 * UI sub-state machine for the active player phase. Phases are pure
 * UI-workflow objects: they hold cursor / hover / draft state, map input
 * events to [battletech.tactical.command.GameCommand]s, and produce render
 * data. They never mutate game state directly — all writes flow through
 * [AppState.session].
 */
internal sealed interface Phase {
    val turnPhase: TurnPhase

    fun handle(event: InputEvent, app: AppState): Transition? = null

    fun render(app: AppState): RenderData = RenderData.EMPTY

    fun prompt(app: AppState): String

    fun selectedUnit(app: AppState): VisibleUnit? = null

    fun unitStatus(app: AppState): VisibleUnit? = selectedUnit(app)

    /**
     * Heat the [selectedUnit] *would* generate if the in-progress declaration
     * (hovered move / selected weapons) were committed. Rendered gray in the
     * UNIT STATUS HEAT panel; empty when there is nothing pending.
     */
    fun pendingHeat(app: AppState): List<HeatSource> = emptyList()

    fun pathDestination(): HexCoordinates? = null

    fun attackRender(app: AppState): AttackRender? = null

    fun targetStatusUnit(app: AppState): ForeignUnit? = null

    /**
     * Phase-local side panels the active phase wants visible. The always-on
     * and cross-phase panels (LOG, UNIT STATUS, ATTACK RESULTS) are decided by
     * [battletech.tui.game.PanelVisibility], not here — a phase only declares
     * the panels that belong to its own workflow.
     */
    fun visiblePanels(app: AppState): Set<PanelId> = emptySet()

    fun declaredTargetsRender(app: AppState): DeclaredTargetsRender? = null

    fun movementMode(): MovementMode? = null

    fun activePlayerLabel(app: AppState): String? = null
}

/**
 * A sub-mode entered from an idle unit-selection state (destination browsing,
 * facing, weapon/physical declaration). Pressing Esc backs out one level via
 * [onCancel], which returns to the parent phase — usually the idle selecting
 * state, or the previous sub-mode in a multi-step flow.
 */
internal interface CancelableSubPhase {
    fun onCancel(app: AppState): Transition
}
