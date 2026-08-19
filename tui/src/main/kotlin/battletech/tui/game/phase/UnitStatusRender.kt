package battletech.tui.game.phase

import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.VisibleUnit

/**
 * The active phase's contribution to the UNIT STATUS panel: the unit under focus (null when the
 * cursor sits over an empty hex — a real state [battletech.tui.view.UnitStatusView] renders as
 * "No unit selected"), plus the heat an in-progress declaration — a hovered move or selected
 * weapons — would generate if committed. [pendingHeat] is meaningless without [subject]; bundling
 * them makes that pairing a type invariant instead of a discipline two independent phase methods
 * had to uphold on their own.
 */
internal data class UnitStatusRender(
    val subject: VisibleUnit?,
    val pendingHeat: List<HeatSource> = emptyList(),
)
