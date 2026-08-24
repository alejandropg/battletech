package battletech.tactical.heat

import battletech.tactical.model.GameMap
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.engineHeatSource

/**
 * The end-of-turn heat preview the TUI's UNIT STATUS panel renders: [current] heat plus what's
 * already [committed] this turn (from [CombatUnit.heatGeneratedThisTurn] plus any active engine
 * crit, via [CombatUnit.engineHeatSource]) plus what's still [pending] (a hovered move /
 * in-progress weapon declaration not yet committed), weighed against [dissipation] capacity
 * (sink capacity plus the water-hex bonus, via [submersionDissipationBonus]).
 *
 * This is the same formula [applyHeatPhase] applies at the real end of turn — [projectHeat]
 * is the authority, and [applyHeatPhase] calls it directly, so the preview and the resolved
 * value can never drift again.
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

/**
 * Builds [unit]'s [HeatProjection] against its already-committed heat plus [pending] preview
 * sources, using [map] for the water-dissipation bonus at the unit's current position.
 */
public fun projectHeat(unit: CombatUnit, map: GameMap, pending: List<HeatSource> = emptyList()): HeatProjection =
    HeatProjection(
        current = unit.currentHeat,
        committed = unit.heatGeneratedThisTurn + listOfNotNull(unit.engineHeatSource()),
        pending = pending,
        dissipation = unit.heatSink.dissipation() + submersionDissipationBonus(unit.position, map),
    )
