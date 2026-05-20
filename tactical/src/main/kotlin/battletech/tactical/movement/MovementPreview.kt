package battletech.tactical.movement

import battletech.tactical.action.ActionPreview

public data class MovementPreview(
    public val reachability: ReachabilityMap,
) : ActionPreview
