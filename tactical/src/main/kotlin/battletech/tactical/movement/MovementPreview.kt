package battletech.tactical.movement

import battletech.tactical.query.ActionPreview

public data class MovementPreview(
    public val reachability: ReachabilityMap,
) : ActionPreview
