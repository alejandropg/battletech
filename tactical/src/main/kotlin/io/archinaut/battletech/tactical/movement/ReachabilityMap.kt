package io.archinaut.battletech.tactical.movement

import io.archinaut.battletech.tactical.model.MovementMode

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.HexDirection

public data class ReachabilityMap(
    public val mode: MovementMode,
    public val maxMP: Int,
    public val destinations: List<ReachableHex>,
) {
    public fun facingsByPosition(): Map<HexCoordinates, Set<HexDirection>> =
        destinations
            .groupBy { it.position }
            .mapValues { (_, hexes) -> hexes.map { it.facing }.toSet() }
}
