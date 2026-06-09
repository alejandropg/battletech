package battletech.tui.game

import battletech.tactical.model.TurnPhase
import battletech.tui.view.AttackResultsView
import battletech.tui.view.DeclaredTargetsView
import battletech.tui.view.LogView
import battletech.tui.view.SidebarView
import battletech.tui.view.TargetStatusView
import battletech.tui.view.TargetsView

public object PanelVisibility {
    public fun visibleIndices(appState: AppState): Set<Int> {
        val visible = mutableSetOf(LogView.INDEX, SidebarView.INDEX)
        if (appState.phase.targetStatusUnit(appState.gameState) != null) visible += TargetStatusView.INDEX
        val attackRender = appState.phase.attackRender(appState.gameState)
        if (attackRender?.targets?.isNotEmpty() == true) visible += TargetsView.INDEX
        // Only the weapon-attack flow populates the declared-targets panel. The
        // dedicated physical-attack flow leaves declaredTargetsRender null, so
        // reserving the column there would render as a blank gap between the
        // tactical map and the attack-results panel.
        if (appState.currentPhase == TurnPhase.WEAPON_ATTACK) visible += DeclaredTargetsView.INDEX
        // Results stay visible from weapon resolution onward (through physical
        // attack + movement). The cascade stops at PHYSICAL_ATTACK, so only
        // WEAPON_ATTACK must hide them.
        if (appState.lastAttackResults != null &&
            appState.currentPhase != TurnPhase.WEAPON_ATTACK
        ) {
            visible += AttackResultsView.INDEX
        }
        return visible
    }
}
