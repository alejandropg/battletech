package battletech.tui.game

import battletech.tactical.model.TurnPhase

/**
 * Decides which side panels are visible this frame, as a set of [PanelId] values. Composes
 * three kinds of owner:
 *
 *  - **Always-on** structural panels (LOG, UNIT STATUS, HELP). HELP is structurally always part
 *    of the layout — `battletech.tui.view.Panel.collapsedWidth` being `0` for HELP plus
 *    [AppState.collapsedPanels] (closed by default) are what actually keep it off screen the
 *    rest of the time.
 *  - **Cross-phase** state-driven panels (ATTACK RESULTS) whose visibility spans
 *    several phases and depends on [AppState] rather than any single phase.
 *  - **Phase-local** panels, delegated to the active phase via
 *    [battletech.tui.game.phase.Phase.visiblePanels].
 *
 * Recomputed every frame, so nothing can go stale.
 */
internal object PanelVisibility {
    fun visiblePanels(appState: AppState): Set<PanelId> = buildSet {
        add(PanelId.LOG)
        add(PanelId.UNIT_STATUS)
        add(PanelId.HELP)

        // Results stay visible from weapon resolution onward (through physical
        // attack + movement). Only the weapon-attack flow hides them.
        if (appState.lastAttackResults != null && appState.currentPhase != TurnPhase.WEAPON_ATTACK) {
            add(PanelId.ATTACK_RESULTS)
        }

        addAll(appState.phase.visiblePanels(appState))
    }
}
