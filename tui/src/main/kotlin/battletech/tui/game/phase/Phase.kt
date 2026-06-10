package battletech.tui.game.phase

import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.PublicUnit
import battletech.tactical.session.TurnState
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource
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

    fun render(gameState: GameState): RenderData = RenderData.EMPTY

    fun prompt(app: AppState): String

    fun selectedUnit(app: AppState): CombatUnit? = null

    /**
     * Heat the [selectedUnit] *would* generate if the in-progress declaration
     * (hovered move / selected weapons) were committed. Rendered gray in the
     * UNIT STATUS HEAT panel; empty when there is nothing pending.
     */
    fun pendingHeat(app: AppState): List<HeatSource> = emptyList()

    fun pathDestination(): HexCoordinates? = null

    fun attackRender(gameState: GameState): AttackRender? = null

    fun targetStatusUnit(gameState: GameState): PublicUnit? = null

    /**
     * Phase-local side panels the active phase wants visible. The always-on
     * and cross-phase panels (LOG, UNIT STATUS, ATTACK RESULTS) are decided by
     * [battletech.tui.game.PanelVisibility], not here — a phase only declares
     * the panels that belong to its own workflow.
     */
    fun visiblePanels(gameState: GameState): Set<PanelId> = emptySet()

    fun declaredTargetsRender(
        gameState: GameState,
        turnState: TurnState,
        viewingPlayer: PlayerId,
    ): DeclaredTargetsRender? = null

    fun movementMode(): MovementMode? = null

    fun activePlayerLabel(app: AppState): String? = null
}
