package io.archinaut.battletech.tui.view

import io.archinaut.tenter.panel.PanelId
import io.archinaut.tenter.panel.PanelSet
import io.archinaut.tenter.panel.PanelState

/**
 * The states [id] steps through over four forward cycles from NORMAL — the observable consequence
 * of which states a [Panel] declares, asserted by driving the set rather than by reading the
 * declaration back. Four steps tells every declared combination apart.
 */
public fun <K : PanelId, I> PanelSet<K, I>.declaredCycle(id: K): List<PanelState> {
    focus(id)
    return List(4) {
        cycleFocusedState(1)
        stateOf(id)
    }
}
