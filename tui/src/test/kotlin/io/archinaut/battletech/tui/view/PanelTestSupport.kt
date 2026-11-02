package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.panel.PanelId
import io.archinaut.tenter.widget.panel.PanelSet
import io.archinaut.tenter.widget.panel.PanelMode

/**
 * The states [id] steps through over four forward cycles from NORMAL — the observable consequence
 * of which states a [Panel] declares, asserted by driving the set rather than by reading the
 * declaration back. Four steps tells every declared combination apart.
 */
public fun <K : PanelId, I> PanelSet<K, I>.declaredCycle(id: K): List<PanelMode> {
    focus(id)
    return List(4) {
        cycleFocusedMode(1)
        modeOf(id)
    }
}
