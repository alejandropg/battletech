package battletech.tactical.movement

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public data class ReachableHex(
    public val position: HexCoordinates,
    public val facing: HexDirection,
    public val mpSpent: Int,
    public val path: List<MovementStep>,
)
