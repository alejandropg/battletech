package battletech.tui.game

import tenter.panel.PanelId

/**
 * Stable identity for every panel: its name and its user-facing `Alt+<badge>` chord /
 * bordered-decoration badge. [badge] is fixed per panel and intentionally **independent of the
 * left-to-right render order** — `Alt+0` always means the BOARD panel regardless of which side
 * panels happen to be visible in the current phase.
 *
 * `Alt+<badge>` focuses that panel — see `tenter.input.ChromeInput.panelKey`'s KDoc. `+`/`-` then
 * cycle the focused panel's [tenter.panel.PanelState]; `tenter.panel.Panel` remembers its own
 * state and scroll, keyed by the [GamePanelId] declared here. `Alt+h` is the one chord with an
 * extra effect: it also opens HELP if closed, or closes it if it was already open and focused —
 * see `AppState.helpOpen`'s KDoc.
 *
 * [BOARD] is the `tenter.panel.PanelSet`'s `main` panel — always visible, never in
 * [PanelVisibility]'s set, and declares only [tenter.panel.PanelState.NORMAL].
 *
 * This enum carries no layout facts of its own — those live on `tenter.panel.Panel`,
 * keyed by the [GamePanelId] declared here.
 */
internal enum class GamePanelId(override val badge: Char) : PanelId {
    BOARD('0'),
    UNIT_STATUS('1'),
    DECLARED_TARGETS('2'),
    TARGETS('3'),
    TARGET_STATUS('4'),
    ATTACK_RESULTS('5'),
    LOG('9'),
    HELP('h'),
    ;

    internal companion object {
        fun byBadge(badge: Char): GamePanelId? = entries.firstOrNull { it.badge == badge }
    }
}
