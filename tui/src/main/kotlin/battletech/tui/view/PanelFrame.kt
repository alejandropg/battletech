package battletech.tui.view

import battletech.tui.game.AppState
import battletech.tui.game.phase.AttackResultsRender

/**
 * The view-model inputs for one render frame, derived from [AppState] once and
 * shared across all panel builders. Fields are `lazy` so data for a panel that
 * is hidden or collapsed (its builder never runs) is never gathered, while a
 * panel rendered more than once would still compute its inputs only once.
 *
 * This is the single place that interprets [AppState] into view inputs; the
 * panels themselves only read these prepared values.
 */
internal class PanelFrame(private val appState: AppState) {

    val visibleState get() = appState.visibleState

    val attackRender by lazy { appState.phase.attackRender(appState) }

    val targetStatusUnit by lazy { appState.phase.targetStatusUnit(appState) }

    val unitStatus by lazy { appState.phase.unitStatus(appState) }

    val pendingHeat by lazy { appState.phase.pendingHeat(appState) }

    val logEntries by lazy { appState.session.logFor(appState.viewer) }

    val declaredTargets by lazy { appState.phase.declaredTargetsRender(appState) }

    val attackResults: AttackResultsRender? by lazy {
        appState.lastAttackResults?.let { results ->
            AttackResultsRender(
                results = results,
                unitOwners = visibleState.units.associate { it.id to it.owner },
                viewer = appState.viewer,
            )
        }
    }
}
