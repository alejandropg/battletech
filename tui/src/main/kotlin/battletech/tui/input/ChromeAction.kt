package battletech.tui.input

import tenter.input.InputAction
import tenter.panel.PanelId

/**
 * Chrome actions that name something in *this* application. They live here rather than in `tenter`
 * because [FocusPanel] names a [PanelId] from one of this application's own panel enums, and
 * `tenter.input` is a leaf that cannot see `tenter.panel`, let alone `battletech`. Keeping the
 * whole CHROME vocabulary in one sealed hierarchy also keeps `runLoop`'s dispatch `when`
 * exhaustive. [FocusPanel.panel] is typed as the general [PanelId] rather than a game-specific enum
 * so the same action family serves both the game screen and the setup screen; each loop only ever
 * sees the panel enum its own layer binds.
 */
internal sealed interface ChromeAction : InputAction {

    data class FocusPanel(val panel: PanelId) : ChromeAction {
        override val id: String get() = "focusPanel.${panel.toString().lowercase()}"
    }

    /** ? is not FocusPanel(HELP): it also decides whether HELP exists this frame. */
    data object ToggleHelp : ChromeAction {
        override val id: String get() = "toggleHelp"
    }

    data class CycleState(val delta: Int) : ChromeAction {
        override val id: String get() = if (delta > 0) "cycleState.next" else "cycleState.previous"
    }

    /** Absorbed by `terminalEvents`' takeWhile upstream of the loop — see Keybindings.isQuit. */
    data object Quit : ChromeAction {
        override val id: String get() = "quit"
    }
}
