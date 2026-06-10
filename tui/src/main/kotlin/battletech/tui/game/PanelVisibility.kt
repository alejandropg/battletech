package battletech.tui.game

import battletech.tactical.model.TurnPhase

/**
 * Decides which side panels are visible this frame, as a set of [PanelId.index]
 * values. Composes three kinds of owner:
 *
 *  - **Always-on** structural panels (LOG, UNIT STATUS).
 *  - **Cross-phase** state-driven panels (ATTACK RESULTS) whose visibility spans
 *    several phases and depends on [AppState] rather than any single phase.
 *  - **Phase-local** panels, delegated to the active phase via
 *    [battletech.tui.game.phase.Phase.visiblePanels].
 *
 * Recomputed every frame, so nothing can go stale.
 */
internal object PanelVisibility {
    fun visibleIndices(appState: AppState): Set<Int> = buildSet {
        add(PanelId.Log.index)
        add(PanelId.UnitStatus.index)

        // Results stay visible from weapon resolution onward (through physical
        // attack + movement). Only the weapon-attack flow hides them.
        if (appState.lastAttackResults != null && appState.currentPhase != TurnPhase.WEAPON_ATTACK) {
            add(PanelId.AttackResults.index)
        }

        appState.phase.visiblePanels(appState.gameState).forEach { add(it.index) }
    }
}
