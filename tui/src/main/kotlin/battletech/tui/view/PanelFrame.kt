package battletech.tui.view

import battletech.tactical.model.PlayerId
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

    val gameState get() = appState.gameState

    val attackRender by lazy { appState.phase.attackRender(gameState) }

    val targetStatusUnit by lazy { appState.phase.targetStatusUnit(gameState) }

    val selectedUnit by lazy { appState.phase.selectedUnit(appState) }

    val pendingHeat by lazy { appState.phase.pendingHeat(appState) }

    val logEntries by lazy { appState.session.gameLog.snapshot() }

    val declaredTargets by lazy {
        val turnState = appState.turnState
        val viewingPlayer =
            if (turnState.attackSequence.order.isEmpty() || turnState.allAttackImpulsesComplete) {
                PlayerId.PLAYER_1
            } else {
                turnState.activeAttackPlayer
            }
        appState.phase.declaredTargetsRender(gameState, turnState, viewingPlayer)
    }

    val attackResults: AttackResultsRender? by lazy {
        appState.lastAttackResults?.let { results ->
            AttackResultsRender(
                results = results,
                unitNames = gameState.units.associate { it.id to it.name },
                unitOwners = gameState.units.associate { it.id to it.owner },
            )
        }
    }
}
