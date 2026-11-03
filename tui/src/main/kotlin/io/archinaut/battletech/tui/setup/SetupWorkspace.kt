package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.terminal.Frame
import io.archinaut.tenter.widget.FlashMessage
import io.archinaut.tenter.widget.panel.PanelHit
import io.archinaut.tenter.widget.panel.PanelSet

/**
 * Owns the setup screen's [PanelSet] for one [SetupApp] run: the controller side (focus, scroll,
 * hit-testing) of the screen whose drawing is [SetupScreen]. Mirrors `io.archinaut.battletech.tui.view.Workspace`.
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

    /** Draws one frame of [SetupScreen] over the whole [frame], with this workspace's panels as its state. */
    fun render(frame: Frame, state: SetupState, flash: FlashMessage?) {
        frame.renderStatefulWidget(SetupScreen(state, keys, flash), frame.area, panels)
    }
}
