package battletech.tui.setup

import battletech.tui.input.Keybindings
import tenter.panel.PanelState
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer
import tenter.view.FlashMessage

/**
 * Owns the [SetupPanelSet] — every setup panel plus the banner chrome — for one [SetupApp] run.
 * Mirrors `battletech.tui.view.Workspace`.
 */
internal class SetupWorkspace(private val keys: Keybindings) {
    private val panels: SetupPanelSet = SetupPanels.build(keys)

    val focused: SetupPanelId get() = panels.focused

    fun focus(id: SetupPanelId) = panels.focus(id)
    fun cycleFocusedState(delta: Int) = panels.cycleFocusedState(delta)
    fun scrollFocused(dx: Int, dy: Int) = panels.scrollFocused(dx, dy)
    fun pageFocused(direction: Int) = panels.pageFocused(direction)
    fun scrollPanel(id: SetupPanelId, delta: Int) = panels.scroll(id, 0, delta)
    fun panelAt(x: Int, y: Int): SetupPanelId? = panels.panelIdAt(x, y)

    /**
     * Composes and draws one frame: the banner chrome (D19) plus every visible panel, laid out in
     * equal-width columns (see [tenter.panel.PanelLayout.computeUniform]), with HELP retaining
     * the shared fixed width used by the game workspace.
     */
    fun render(
        state: SetupState,
        width: Int,
        height: Int,
        flash: FlashMessage?,
        forgetReveal: Boolean = false,
    ): ScreenBuffer {
        val visible = SetupPanelVisibility.visiblePanels(state)
        val bannerHeight = SetupBannerView.reservedHeight(width)

        val buffer = ScreenBuffer(width, height)
        val screen = Canvas.of(buffer)
        val inputs = SetupPanelInputs(state, keys)
        val minimizedCount = panels.sides.count { it.id in visible && it.state == PanelState.MINIMIZED }
        val uniformColumnCount = if (minimizedCount == 0) SETUP_PANEL_COUNT else {
            (SETUP_PANEL_COUNT - minimizedCount).coerceAtLeast(0)
        }

        panels.render(
            screen,
            inputs,
            visible,
            reservedTop = bannerHeight,
            forgetReveal = forgetReveal,
            uniformColumnCount = uniformColumnCount,
            fixedWidthPanels = setOf(SetupPanelId.HELP),
        )

        val prompt = flash?.text ?: defaultPrompt(state)
        SetupBannerView(prompt).draw(screen.region(0, 0, width, bannerHeight))

        return buffer
    }

    private companion object {
        private const val SETUP_PANEL_COUNT = 4
    }
}

private fun defaultPrompt(state: SetupState): String = when {
    state.readOnly -> "waiting for the host to start the match…"
    !state.modeLocked -> "space: toggle mode   c: lock it in"
    !state.rostersVisible -> "waiting for player 2 to connect…"
    else -> "select a map and units for each player, then press c to start"
}
