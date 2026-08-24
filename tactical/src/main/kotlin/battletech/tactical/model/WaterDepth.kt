package battletech.tactical.model

/**
 * Returns the water depth (in levels) of the hex at [position], or 0 if the hex is
 * dry or absent from [map]. What each depth does to a unit is owned by
 * `docs/rules/water.md`.
 *
 * This is the single authoritative query for water depth across the engine. Call sites in
 * `PhysicalReachRules`, `LineOfSight`, `SubmergedWeaponRule`, `HeatPhaseHandler`, and
 * `HeatProjection` all delegate here rather than reading `map.hexes[position]?.depth` directly.
 *
 * Takes a bare [position] and [map] — not a unit and a [GameState] — because that is
 * genuinely all it reads. That keeps it callable from the per-viewer query path, where the
 * unit in hand may be a [battletech.tactical.unit.ForeignUnit] carrying only public
 * fields (position among them) and no [GameState] is available at all. Same rationale as
 * [battletech.tactical.attack.lineOfSight]'s position-only signature — and the reason
 * `battletech.tactical.heat.submersionDissipationBonus` takes the same shape.
 */
public fun unitWaterDepth(position: HexCoordinates, map: GameMap): Int =
    map.hexes[position]?.depth ?: 0
