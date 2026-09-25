package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.game.phase.AttackRender
import io.archinaut.battletech.tui.game.phase.AttackResultsRender
import io.archinaut.battletech.tui.game.phase.DeclaredTargetsRender
import io.archinaut.battletech.tui.input.ContextId
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.input.KeySection
import io.archinaut.tenter.view.ContentView
import io.archinaut.tenter.view.fixedContent

/**
 * The view-model inputs for one render frame, derived from [AppState] once and
 * shared across all panel builders. Fields are `lazy` so data for a panel that
 * is hidden or minimized (its builder never runs) is never gathered, while a
 * panel rendered more than once would still compute its inputs only once.
 *
 * This is the single place that interprets [AppState] into view inputs; the
 * panels themselves only read these prepared values.
 */
internal class PanelInputs(private val appState: AppState, private val keys: Keybindings) {

    val state get() = appState.state

    private val renderData by lazy { appState.phase.board(appState) }

    /** The tactical board's view — see [Panels.build]'s board [io.archinaut.tenter.panel.Panel]. */
    val boardView: ContentView by lazy {
        val board = BoardView(
            appState.state,
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
        val (width, height) = BoardView.contentSize(appState.state.map)
        fixedContent(width, height, board)
    }

    /**
     * The active phase's side-panel contributions, computed once per frame. [PhasePanels.ids]
     * (via [io.archinaut.battletech.tui.game.PanelVisibility]) already decided which of TARGETS/TARGET_STATUS/
     * DECLARED_TARGETS exist this frame from these same fields, so [attackRender], [targetStatusUnit],
     * and [declaredTargets] can assume their content is present — see [orMissing].
     */
    private val phasePanels by lazy { appState.phase.panels(appState) }

    /** This frame's attack render, for the TARGETS panel. Non-null by construction — see [phasePanels]. */
    val attackRender: AttackRender by lazy { phasePanels.targets.orMissing(GamePanelId.TARGETS) }

    /** This frame's target-status subject, for the TARGET STATUS panel. Non-null by construction — see [phasePanels]. */
    val targetStatusUnit: ForeignUnit by lazy { phasePanels.targetStatus.orMissing(GamePanelId.TARGET_STATUS) }

    val unitStatus by lazy { appState.phase.unitStatus(appState) }

    val logEntries by lazy { appState.log }

    /** This frame's declared targets. Non-null by construction — see [phasePanels]. */
    val declaredTargets: DeclaredTargetsRender by lazy {
        phasePanels.declaredTargets.orMissing(GamePanelId.DECLARED_TARGETS).value
    }

    /** This frame's attack results. Non-null by construction — [io.archinaut.battletech.tui.game.PanelVisibility] shows the panel only when [AppState.lastAttackResults] is set. */
    val attackResults: AttackResultsRender by lazy {
        val results = appState.lastAttackResults
            ?: error("ATTACK RESULTS panel built with no results — PanelVisibility should have hidden it")
        AttackResultsRender(
            results = results,
            units = state.units,
            viewer = appState.viewer,
        )
    }

    val helpSections: List<KeySection> by lazy {
        listOf(keys.hints(appState.phase.keyContext), keys.hints(ContextId.CHROME))
    }
}

/** The one place "the host showed a panel the phase did not contribute" becomes a failure. */
private fun <T : Any> T?.orMissing(id: GamePanelId): T = this
    ?: error("$id built with no content — PhasePanels.ids should have hidden it")
