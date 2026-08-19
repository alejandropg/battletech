package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import battletech.tui.game.AppState
import battletech.tui.game.GamePanelId
import battletech.tui.game.phase.AttackRender
import battletech.tui.game.phase.AttackResultsRender
import battletech.tui.game.phase.DeclaredTargetsRender
import tenter.input.KeySection
import battletech.tui.input.Keymap
import tenter.view.ContentExtent
import tenter.view.View

/**
 * The view-model inputs for one render frame, derived from [AppState] once and
 * shared across all panel builders. Fields are `lazy` so data for a panel that
 * is hidden or minimized (its builder never runs) is never gathered, while a
 * panel rendered more than once would still compute its inputs only once.
 *
 * This is the single place that interprets [AppState] into view inputs; the
 * panels themselves only read these prepared values.
 */
internal class PanelInputs(private val appState: AppState) {

    val visibleState get() = appState.visibleState

    private val renderData by lazy { appState.phase.board(appState) }

    /** The tactical board's view — see [Panels.build]'s board [tenter.panel.Panel]. */
    val boardView: View by lazy {
        BoardView(
            appState.visibleState,
            cursorPosition = appState.cursor,
            hexHighlights = renderData.hexHighlights,
            reachableFacings = renderData.reachableFacings,
            facingSelectionFacings = renderData.facingSelection?.facings,
            pathDestination = renderData.pathDestination,
            movementMode = renderData.movementMode,
            draftTorsoFacings = renderData.draftTorsoFacings,
            validTargetPositions = renderData.validTargetPositions,
            selectedTargetPosition = renderData.selectedTargetPosition,
        )
    }

    /** The board's fixed content extent — the map's own size, not measured from its content. */
    val boardExtent: ContentExtent by lazy {
        val (width, height) = BoardView.contentSize(appState.visibleState.map)
        ContentExtent.Fixed(width, height)
    }

    /**
     * The active phase's side-panel contributions, computed once per frame. [PhasePanels.ids]
     * (via [battletech.tui.game.PanelVisibility]) already decided which of TARGETS/TARGET_STATUS/
     * DECLARED_TARGETS exist this frame from these same fields, so [attackRender], [targetStatusUnit],
     * and [declaredTargets] can assume their content is present — see [orMissing].
     */
    private val phasePanels by lazy { appState.phase.panels(appState) }

    /** This frame's attack render, for the TARGETS panel. Non-null by construction — see [phasePanels]. */
    val attackRender: AttackRender by lazy { phasePanels.targets.orMissing(GamePanelId.TARGETS) }

    /** This frame's target-status subject, for the TARGET STATUS panel. Non-null by construction — see [phasePanels]. */
    val targetStatusUnit: ForeignUnit by lazy { phasePanels.targetStatus.orMissing(GamePanelId.TARGET_STATUS) }

    val unitStatus by lazy { appState.phase.unitStatus(appState) }

    val logEntries by lazy { appState.logFor(appState.viewer) }

    /** This frame's declared targets. Non-null by construction — see [phasePanels]. */
    val declaredTargets: DeclaredTargetsRender by lazy {
        phasePanels.declaredTargets.orMissing(GamePanelId.DECLARED_TARGETS).value
    }

    /** This frame's attack results. Non-null by construction — [battletech.tui.game.PanelVisibility] shows the panel only when [AppState.lastAttackResults] is set. */
    val attackResults: AttackResultsRender by lazy {
        val results = appState.lastAttackResults
            ?: error("ATTACK RESULTS panel built with no results — PanelVisibility should have hidden it")
        AttackResultsRender(
            results = results,
            units = visibleState.units,
            viewer = appState.viewer,
        )
    }

    val helpSections: List<KeySection> by lazy {
        listOf(appState.phase.keySection(), Keymap.GLOBAL)
    }
}

/** The one place "the host showed a panel the phase did not contribute" becomes a failure. */
private fun <T : Any> T?.orMissing(id: GamePanelId): T = this
    ?: error("$id built with no content — PhasePanels.ids should have hidden it")
