package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.screen.Layout
import io.archinaut.tenter.widget.FlashMessage
import io.archinaut.tenter.widget.StatefulWidget
import io.archinaut.tenter.widget.panel.PanelSet
import io.archinaut.tenter.widget.panel.Panels

/**
 * The whole setup screen for one frame: the banner chrome (D19) on top, and every visible panel
 * below it, placed by [SetupPanels.areas]. Its state is the [SetupWorkspace]'s [PanelSet].
 * Mirrors `io.archinaut.battletech.tui.view.GameScreen`.
 *
 * Render it over the whole frame: [Panels] places panels by frame coordinates, and this widget
 * hands it areas split from its own canvas.
 */
internal class SetupScreen(
    private val setup: SetupState,
    private val keys: Keybindings,
    private val flash: FlashMessage?,
) : StatefulWidget<PanelSet<SetupPanelId, SetupPanelInputs>> {

    override fun render(canvas: Canvas, state: PanelSet<SetupPanelId, SetupPanelInputs>) {
        val (bannerArea, body) = Layout.vertical(
            Constraint.Length(SetupBannerView.reservedHeight(canvas.width)),
            Constraint.Fill(1),
        ).split(canvas.area)

        val inputs = SetupPanelInputs(setup, keys)
        val areas = SetupPanels.areas(state, inputs, SetupPanelVisibility.visiblePanels(setup), body)
        Panels(inputs, areas).render(canvas.region(body), state)
        SetupBannerView(flash?.text ?: defaultPrompt(setup)).render(canvas.region(bannerArea))
    }
}

private fun defaultPrompt(state: SetupState): String = when {
    state.readOnly -> "waiting for the host…   ↑↓: browse   +: maximize"
    !state.modeLocked -> "space: toggle mode   c: lock it in"
    !state.rostersVisible -> "waiting for player 2 to connect…"
    else -> "select a map and units for each player, then press c to start"
}
