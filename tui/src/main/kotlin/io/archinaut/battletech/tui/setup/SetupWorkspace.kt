package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.screen.Layout
import io.archinaut.tenter.terminal.Frame
import io.archinaut.tenter.widget.FlashMessage
import io.archinaut.tenter.widget.panel.PanelHit
import io.archinaut.tenter.widget.panel.PanelSet
import io.archinaut.tenter.widget.panel.Panels

/**
 * Owns the setup screen's [PanelSet] — every setup panel plus the banner chrome — for one [SetupApp] run.
 * Mirrors `io.archinaut.battletech.tui.view.Workspace`.
 */
internal class SetupWorkspace(private val keys: Keybindings) {
    private val panels: PanelSet<SetupPanelId, SetupPanelInputs> = SetupPanels.build(keys)

    val focused: SetupPanelId get() = panels.focused

    fun focus(id: SetupPanelId) = panels.focus(id)

    /** Focuses [id], or cycles it forward when it is already focused — a panel's number key. */
    internal fun focusOrCycle(id: SetupPanelId) {
        if (id == panels.focused) panels.cycleFocusedMode(1) else panels.focus(id)
    }

    fun cycleFocusedMode(delta: Int) = panels.cycleFocusedMode(delta)
    fun scrollFocused(dx: Int, dy: Int) = panels.scroll(panels.focused, dx, dy)
    fun pageFocused(direction: Int) = panels.page(panels.focused, direction)
    fun scrollPanel(id: SetupPanelId, delta: Int) = panels.scroll(id, 0, delta)

    /** Resolves a completed-frame hit without reproducing border, padding, or scroll arithmetic. */
    fun hitTest(x: Int, y: Int): PanelHit<SetupPanelId>? = panels.hitTest(x, y)

    /**
     * Composes and draws one frame: the banner chrome (D19) on top, and every visible panel below
     * it, placed by [SetupPanels.areas].
     */
    fun render(frame: Frame, state: SetupState, flash: FlashMessage?) {
        val (bannerArea, body) = Layout.vertical(
            Constraint.Length(SetupBannerView.reservedHeight(frame.area.width)),
            Constraint.Fill(1),
        ).split(frame.area)

        val inputs = SetupPanelInputs(state, keys)
        val areas = SetupPanels.areas(panels, inputs, SetupPanelVisibility.visiblePanels(state), body)
        frame.renderStatefulWidget(Panels(inputs, areas), body, panels)
        frame.renderWidget(SetupBannerView(flash?.text ?: defaultPrompt(state)), bannerArea)
    }
}

private fun defaultPrompt(state: SetupState): String = when {
    state.readOnly -> "waiting for the host…   ↑↓: browse   +: maximize"
    !state.modeLocked -> "space: toggle mode   c: lock it in"
    !state.rostersVisible -> "waiting for player 2 to connect…"
    else -> "select a map and units for each player, then press c to start"
}
