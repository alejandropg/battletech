package battletech.tui.game

import tenter.panel.PanelId

/**
 * Stable identity for every panel. Its user-facing `Alt+<key>` focus chord and its bordered-
 * decoration badge are no longer carried here — both are derived from `battletech.tui.input.
 * Keybindings.badgeFor`, sourced from whichever chord the `CHROME` key layer binds to
 * `FocusPanel`/`ToggleHelp` for this panel, so a rebinding relabels the border too.
 *
 * `+`/`-` cycle the focused panel's [tenter.panel.PanelState]; `tenter.panel.Panel` remembers its
 * own state and scroll, keyed by the [GamePanelId] declared here. `Alt+h` is the one chord with an
 * extra effect: it also opens HELP if closed, or closes it if it was already open and focused —
 * see `AppState.helpOpen`'s KDoc.
 *
 * [BOARD] is the `tenter.panel.PanelSet`'s `main` panel — always visible, never in
 * [PanelVisibility]'s set, and declares only [tenter.panel.PanelState.NORMAL].
 *
 * This enum carries no layout facts of its own — those live on `tenter.panel.Panel`,
 * keyed by the [GamePanelId] declared here.
 */
internal enum class GamePanelId : PanelId {
    BOARD,
    UNIT_STATUS,
    DECLARED_TARGETS,
    TARGETS,
    TARGET_STATUS,
    ATTACK_RESULTS,
    LOG,
    HELP,
}
