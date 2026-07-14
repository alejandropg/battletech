package battletech.tactical.heat

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource

/**
 * The end-of-turn heat preview the TUI's UNIT STATUS panel renders: [current] heat plus what's
 * already [committed] this turn (from [CombatUnit.heatGeneratedThisTurn]) plus what's still
 * [pending] (a hovered move / in-progress weapon declaration not yet committed), weighed against
 * [dissipation] capacity.
 *
 * TODO(known discrepancy — do not fix without a product decision): this reproduces the TUI's
 * legacy preview formula exactly, which deliberately diverges from the heat-phase resolution
 * that actually applies at end of turn ([HeatPhaseResolution]/`applyHeatPhase`): it ignores
 * engine-crit heat generation and the water-hex dissipation bonus. Correcting either would change
 * rendered numbers — that's a product call, not a refactor, so it's flagged here rather than
 * silently fixed.
 */
public data class HeatProjection(
    public val current: Int,
    public val committed: List<HeatSource>,
    public val pending: List<HeatSource>,
    public val dissipation: Int,
) {
    /** Total heat generated this turn: committed + pending. */
    public val generated: Int get() = committed.sumOf { it.amount } + pending.sumOf { it.amount }

    /** Heat actually carried off by sinks, capped at [dissipation] capacity. */
    public val dissipated: Int get() = minOf(current + generated, dissipation)

    /** Heat remaining after dissipation, floored at zero. */
    public val projected: Int get() = (current + generated - dissipation).coerceAtLeast(0)
}

/** Builds [unit]'s [HeatProjection] against its already-committed heat plus [pending] preview sources. */
public fun projectHeat(unit: CombatUnit, pending: List<HeatSource>): HeatProjection = HeatProjection(
    current = unit.currentHeat,
    committed = unit.heatGeneratedThisTurn,
    pending = pending,
    dissipation = unit.heatSink.dissipation(),
)
