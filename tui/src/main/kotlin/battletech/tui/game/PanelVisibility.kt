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
        val isAttackPhase = appState.currentPhase == TurnPhase.WEAPON_ATTACK ||
            appState.currentPhase == TurnPhase.PHYSICAL_ATTACK
        if (isAttackPhase) visible += DeclaredTargetsView.INDEX
        // Results stay visible from weapon resolution onward (through physical
        // attack + movement). The cascade stops at PHYSICAL_ATTACK, so only
        // WEAPON_ATTACK must hide them; during PHYSICAL_ATTACK both panels show.
        if (appState.lastAttackResults != null &&
            appState.currentPhase != TurnPhase.WEAPON_ATTACK
        ) {
            visible += AttackResultsView.INDEX
        }
        return visible
    }
}
