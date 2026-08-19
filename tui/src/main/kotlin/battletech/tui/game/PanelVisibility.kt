package battletech.tui.game

import battletech.tactical.model.TurnPhase

/**
 * Decides which SIDE panels EXIST this frame, as a set of [GamePanelId] values — never [GamePanelId.BOARD],
 * which is the `tenter.panel.PanelSet`'s `main` panel and is always present regardless of this set.
 * Composes four kinds of owner:
 *
 *  - **Always-on** structural panels (LOG, UNIT STATUS).
 *  - **User-opened** panels (HELP) — [AppState.helpOpen], toggled by `Alt+h`. Unlike every other
 *    panel's `Alt+<key>`, which focuses a panel that already exists (a display preference owned
 *    by `tenter.panel.Panel` itself), `Alt+h` toggles whether HELP exists AT ALL — a different
 *    action, so it is a different piece of state, and one that belongs here rather than on the
 *    panel (see [AppState.helpOpen]'s KDoc).
 *  - **Cross-phase** state-driven panels (ATTACK RESULTS) whose visibility spans
 *    several phases and depends on [AppState] rather than any single phase.
 *  - **Phase-local** panels, delegated to the active phase via
 *    [battletech.tui.game.phase.Phase.panels]'s [battletech.tui.game.phase.PhasePanels.ids].
 *
 * Recomputed every frame, so nothing can go stale.
 */
internal object PanelVisibility {
    fun visiblePanels(appState: AppState): Set<GamePanelId> = buildSet {
        add(GamePanelId.LOG)
        add(GamePanelId.UNIT_STATUS)
        if (appState.helpOpen) add(GamePanelId.HELP)

        // Results stay visible from weapon resolution onward (through physical
        // attack + movement). Only the weapon-attack flow hides them.
        if (appState.lastAttackResults != null && appState.currentPhase != TurnPhase.WEAPON_ATTACK) {
            add(GamePanelId.ATTACK_RESULTS)
        }

        addAll(appState.phase.panels(appState).ids)
    }
}
