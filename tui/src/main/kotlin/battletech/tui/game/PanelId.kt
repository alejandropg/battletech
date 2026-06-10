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
    Log(0),
    UnitStatus(1),
    DeclaredTargets(2),
    Targets(3),
    TargetStatus(4),
    AttackResults(5),
}
