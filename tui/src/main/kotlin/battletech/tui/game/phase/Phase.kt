package battletech.tui.game.phase

import battletech.tactical.model.TurnPhase
import battletech.tui.game.AppState
import battletech.tui.game.RenderData
import com.github.ajalt.mordant.input.InputEvent
import tenter.input.KeySection

/**
 * UI sub-state machine for the active player phase. Phases are pure
 * UI-workflow objects: they hold cursor / hover / draft state, map input
 * events to [battletech.tactical.command.GameCommand]s, and produce render
 * data. They never mutate game state directly — all writes flow through
 * [AppState.submitCommand].
 */
internal sealed interface Phase {
    val turnPhase: TurnPhase

    /** Null means this phase does not consume [event] — a real three-valued protocol [battletech.tui.loop.runLoop] relies on. */
    fun handle(event: InputEvent, app: AppState): Transition?

    fun board(app: AppState): RenderData = RenderData.EMPTY

    /** This phase's contribution to the status bar: the prompt, and the active player if any. */
    fun status(app: AppState): PhaseStatus

    /**
     * This phase's contribution to the UNIT STATUS panel: the focused unit (if any) plus the heat
     * an in-progress declaration would generate if committed. See [UnitStatusRender]'s KDoc for
     * why the two are bundled.
     */
    fun unitStatus(app: AppState): UnitStatusRender

    /**
     * Phase-local side panels the active phase wants visible, as content — see [PhasePanels]'s
     * KDoc for why presence and visibility are the same fact here.
     */
    fun panels(app: AppState): PhasePanels = PhasePanels.NONE

    /** This phase's local keys, shown as their own section in the HELP panel. */
    fun keySection(): KeySection
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
