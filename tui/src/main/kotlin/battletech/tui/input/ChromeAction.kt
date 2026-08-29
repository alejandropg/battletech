package battletech.tui.input

import battletech.tui.game.GamePanelId
import tenter.input.InputAction

/**
 * Chrome actions that name something in *this* application. They live here rather than in `tenter`
 * because [FocusPanel] names a [GamePanelId], and `tenter.input` is a leaf that cannot see
 * `tenter.panel`, let alone `battletech`. Keeping the whole CHROME vocabulary in one sealed
 * hierarchy also keeps `runLoop`'s dispatch `when` exhaustive.
 */
internal sealed interface ChromeAction : InputAction {

    data class FocusPanel(val panel: GamePanelId) : ChromeAction {
        override val id: String get() = "focusPanel.${panel.name.lowercase()}"
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
