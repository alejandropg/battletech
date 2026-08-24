package battletech.tactical.heat

import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.unitWaterDepth

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
 *
 * Takes a bare [position] and [map] — not a unit and a [battletech.tactical.model.GameState] —
 * for the same reason [unitWaterDepth] does: this must be callable from the per-viewer
 * projection path ([projectHeat]), where no [battletech.tactical.model.GameState] is available.
 */
public fun submersionDissipationBonus(position: HexCoordinates, map: GameMap): Int =
    when (unitWaterDepth(position, map)) {
        0 -> 0
        1 -> 6
        else -> 12
    }
