package battletech.tui.game

/**
 * Stable identity for each side panel: its name and its user-facing `Alt+<key>` chord /
 * `Bordered` decoration badge. [key] is fixed per panel and intentionally **independent of the
 * left-to-right render order** — `Alt+0` always means the LOG panel regardless of which panels
 * happen to be visible in the current phase.
 *
 * `Alt+<key>` toggles a display preference for every panel EXCEPT HELP: `battletech.tui.view.Panel`
 * remembers its own collapsed-vs-expanded state, keyed by the [PanelId] declared here. `Alt+h` is
 * the one exception — it toggles `AppState.helpOpen` instead, since HELP's key controls whether it
 * EXISTS this frame rather than how it's displayed; see `AppState.helpOpen`'s KDoc.
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
