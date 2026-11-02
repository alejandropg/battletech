package io.archinaut.battletech.tui.input

import io.archinaut.tenter.widget.panel.PanelId

/**
 * Chrome actions: panel focus and sizing, help, and quit. Keeping the whole CHROME vocabulary in
 * one sealed hierarchy keeps `runLoop`'s dispatch `when` exhaustive. [FocusPanel.panel] is typed as the general [PanelId] rather than a game-specific enum
 * so the same action family serves both the game screen and the setup screen; each loop only ever
 * sees the panel enum its own layer binds.
 */
internal sealed interface ChromeAction : Action {

    data class FocusPanel(val panel: PanelId) : ChromeAction

    /** ? is not FocusPanel(HELP): it also decides whether HELP exists this frame. */
    data object ToggleHelp : ChromeAction

    data class CycleState(val delta: Int) : ChromeAction

    /** Ends event collection upstream of the loop's dispatch — see Keybindings.isQuit. */
    data object Quit : ChromeAction
}
