package battletech.tui.game

/**
 * Stable identity for each side panel.
 *
 * [index] is the user-facing collapse-toggle digit (Alt+N) and the `drawBox`
 * decoration id. It is fixed per panel and intentionally **independent of the
 * left-to-right render order** — `Alt+0` always means the LOG panel regardless
 * of which panels happen to be visible in the current phase.
 *
 * This enum is the single source of those numeric ids; `View.INDEX` constants
 * derive from it.
 */
internal enum class PanelId(val index: Int) {
    LOG(0),
    UNIT_STATUS(1),
    DECLARED_TARGETS(2),
    TARGETS(3),
    TARGET_STATUS(4),
    ATTACK_RESULTS(5),
}
