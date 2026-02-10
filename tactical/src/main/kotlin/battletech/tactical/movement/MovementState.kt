package battletech.tactical.movement

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public data class MovementState(
    public val position: HexCoordinates,
    public val facing: HexDirection,
)
