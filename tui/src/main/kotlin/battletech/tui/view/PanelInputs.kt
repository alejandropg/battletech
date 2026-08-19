package battletech.tui.view

import battletech.tui.game.AppState
import battletech.tui.game.phase.AttackResultsRender
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

    private val renderData by lazy { appState.phase.render(appState) }

    /** The tactical board's view — see [Panels.build]'s board [tenter.panel.Panel]. */
    val boardView: View by lazy {
        BoardView(
            appState.visibleState,
            cursorPosition = appState.cursor,
            hexHighlights = renderData.hexHighlights,
            reachableFacings = renderData.reachableFacings,
            facingSelectionFacings = renderData.facingSelection?.facings,
            pathDestination = appState.phase.pathDestination(),
            movementMode = appState.phase.movementMode(),
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

    val attackRender by lazy { appState.phase.attackRender(appState) }

    val targetStatusUnit by lazy { appState.phase.targetStatusUnit(appState) }

    val unitStatus by lazy { appState.phase.unitStatus(appState) }

    val pendingHeat by lazy { appState.phase.pendingHeat(appState) }

    val logEntries by lazy { appState.logFor(appState.viewer) }

    val declaredTargets by lazy { appState.phase.declaredTargetsRender(appState) }

    val attackResults: AttackResultsRender? by lazy {
        appState.lastAttackResults?.let { results ->
            AttackResultsRender(
                results = results,
                units = visibleState.units,
                viewer = appState.viewer,
            )
        }
    }

    val helpSections: List<KeySection> by lazy {
        listOf(
            KeySection(appState.phase.keyContext(), appState.phase.keyHints()),
            Keymap.GLOBAL,
        )
    }
}
