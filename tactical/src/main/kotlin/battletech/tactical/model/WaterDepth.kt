package battletech.tactical.model

import battletech.tactical.unit.CombatUnit

/**
 * Returns the water depth (in levels) of the hex at [position], or 0 if the hex is
 * dry or absent from [map].
 *
 * - Depth 0: dry land.
 * - Depth 1: legs submerged — the target gains **partial cover** (+3 to-hit, leg hits
 *   no-effect) and heat sinks dissipate extra heat.
 * - Depth 2+: fully submerged — surface (non-[battletech.tactical.unit.Weapon.underwaterCapable])
 *   weapons cannot fire; a prone unit risks drowning (1 pilot hit per Heat Phase).
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
 * (`docs/missing-rules.md` §Water & Depth — ASSUMPTION/standard BattleTech).
 *
 * Since per-location heat-sink placement is not tracked granularly in the current model,
 * fixed bonuses are applied based on submersion level:
 *
 *  - **Depth 1** (legs submerged): +6. Approximates ~3 double heat sinks in the legs
 *    and lower torso dissipating at twice the normal rate while immersed.
 *  - **Depth 2+** (fully submerged): +12. All heat sinks benefit from full immersion;
 *    doubling the depth-1 approximation.
 *
 * A future stage could scale this by [battletech.tactical.unit.HeatSink.units] if
 * per-location heat-sink placement is added to the model.
 */
public fun submersionDissipationBonus(unit: CombatUnit, gameState: GameState): Int =
    when (unitWaterDepth(unit.position, gameState.map)) {
        0 -> 0
        1 -> 6
        else -> 12
    }
