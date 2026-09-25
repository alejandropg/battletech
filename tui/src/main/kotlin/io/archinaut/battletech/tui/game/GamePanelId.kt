package io.archinaut.battletech.tui.game

import io.archinaut.tenter.panel.PanelId

/**
 * Stable identity for every panel. Its user-facing `Alt+<key>` focus chord and its bordered-
 * decoration badge are no longer carried here — both are derived from `io.archinaut.battletech.tui.input.
 * Keybindings.badgeFor`, sourced from whichever chord the `CHROME` key layer binds to
 * `FocusPanel`/`ToggleHelp` for this panel, so a rebinding relabels the border too.
 *
 * `+`/`-` cycle the focused panel's [io.archinaut.tenter.panel.PanelState], as does pressing a focused panel's
 * number key; `io.archinaut.tenter.panel.Panel` remembers its own state and scroll, keyed by the [GamePanelId]
 * declared here. `?` is the one chord with an extra effect: it also opens HELP if closed, or
 * closes it if it was already open and focused — see `AppState.helpOpen`'s KDoc.
 *
 * [BOARD] is the `io.archinaut.tenter.panel.PanelSet`'s `main` panel — always visible, never in
 * [PanelVisibility]'s set, and declares only [io.archinaut.tenter.panel.PanelState.NORMAL].
 *
 * This enum carries no layout facts of its own — those live on `io.archinaut.tenter.panel.Panel`,
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
