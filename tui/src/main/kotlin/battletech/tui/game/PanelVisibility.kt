package battletech.tui.game

import battletech.tactical.model.TurnPhase
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
        return visible
    }
}
