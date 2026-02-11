package battletech.tactical.action.movement

import battletech.tactical.action.ActionPreview
import battletech.tactical.movement.ReachabilityMap

public data class MovementPreview(
    public val reachability: ReachabilityMap,
) : ActionPreview
