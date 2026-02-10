package battletech.tactical.movement

import battletech.tactical.model.MovementMode

public data class ReachabilityMap(
    public val mode: MovementMode,
    public val maxMP: Int,
    public val destinations: List<ReachableHex>,
)
