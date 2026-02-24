package battletech.tactical.movement

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode

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
