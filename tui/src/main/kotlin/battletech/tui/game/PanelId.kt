package battletech.tui.game

/**
 * Stable identity for each side panel: its name and its user-facing `Alt+<key>` chord /
 * `drawBox` decoration badge. [key] is fixed per panel and intentionally **independent of the
 * left-to-right render order** — `Alt+0` always means the LOG panel regardless of which panels
 * happen to be visible in the current phase.
 *
 * `Alt+<key>` always toggles the panel's membership in `AppState.collapsedPanels`; what that
 * looks like on screen is `battletech.tui.view.Panel.collapsedWidth`.
 *
 * This enum carries no layout facts of its own — those live on `battletech.tui.view.Panel`,
 * keyed by the [PanelId] declared here.
 */
internal enum class PanelId(val key: Char) {
    LOG('0'),
    UNIT_STATUS('1'),
    DECLARED_TARGETS('2'),
    TARGETS('3'),
    TARGET_STATUS('4'),
    ATTACK_RESULTS('5'),
    HELP('h'),
    ;

    internal companion object {
        fun byKey(key: Char): PanelId? = entries.firstOrNull { it.key == key }
    }
}
