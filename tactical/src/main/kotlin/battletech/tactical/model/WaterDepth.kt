package battletech.tactical.model

import battletech.tactical.unit.CombatUnit

/**
 * Returns the water depth (in levels) of the hex at [position], or 0 if the hex is
 * dry or absent from [map]. What each depth does to a unit is owned by
 * `docs/rules/water.md`.
 *
 * This is the single authoritative query for water depth across the engine. Call sites in
 * `PhysicalReachRules`, `LineOfSight`, `SubmergedWeaponRule`, `HeatPhaseHandler`, and
 * `GameStateHeatTransform` all delegate here rather than reading
 * `map.hexes[position]?.depth` directly.
 *
 * Takes a bare [position] and [map] — not a unit and a [GameState] — because that is
 * genuinely all it reads. That keeps it callable from the per-viewer query path, where the
 * unit in hand may be a [battletech.tactical.unit.ForeignUnit] carrying only public
 * fields (position among them) and no [GameState] is available at all. Same rationale as
 * [battletech.tactical.attack.lineOfSight]'s position-only signature.
 */
public fun unitWaterDepth(position: HexCoordinates, map: GameMap): Int =
    map.hexes[position]?.depth ?: 0

/**
 * Extra heat dissipation granted when a unit is standing in water
 * (`docs/rules/heat.md` §1 owns the water heat-sink bonus; the specific +6/+12 values
 * below are an ASSUMPTION — the doc states the bonus qualitatively, not numerically).
 *
 * Since per-location heat-sink placement is not tracked granularly in the current model,
 * a flat bonus per submersion level stands in: +6 at depth 1 approximates ~3 double heat
 * sinks in the legs and lower torso running at twice the normal rate, and depth 2+ doubles
 * that for full immersion. Scaling by [battletech.tactical.unit.HeatSink.units] would need
 * per-location placement in the model first.
 */
public fun submersionDissipationBonus(unit: CombatUnit, gameState: GameState): Int =
    when (unitWaterDepth(unit.position, gameState.map)) {
        0 -> 0
        1 -> 6
        else -> 12
    }
