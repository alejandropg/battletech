package battletech.tui.game

/**
 * Stable identity for each side panel.
 *
 * [key] is the user-facing `Alt+<key>` chord and the `drawBox` decoration badge. It is fixed
 * per panel and intentionally **independent of the left-to-right render order** — `Alt+0`
 * always means the LOG panel regardless of which panels happen to be visible in the current
 * phase.
 *
 * `Alt+<key>` always toggles the panel's membership in `AppState.collapsedPanels`; what that
 * looks like on screen is [hidden]: `false` (the default) shrinks the panel to the narrow
 * collapsed stub, `true` removes it from the layout entirely — no stub, no box, its columns
 * returned to the board. HELP is the only [hidden] panel: it is closed by default and opened
 * on demand rather than shrunk to a stub.
 *
 * This enum is the single source of those keys; `View.KEY` constants derive from it.
 */
internal enum class PanelId(val key: Char, val hidden: Boolean = false) {
    LOG('0'),
    UNIT_STATUS('1'),
    DECLARED_TARGETS('2'),
    TARGETS('3'),
    TARGET_STATUS('4'),
    ATTACK_RESULTS('5'),
    HELP('h', hidden = true),
    ;

    internal companion object {
        fun byKey(key: Char): PanelId? = entries.firstOrNull { it.key == key }
    }
}
