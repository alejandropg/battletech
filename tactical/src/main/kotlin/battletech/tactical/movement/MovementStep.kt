package battletech.tactical.movement

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import kotlinx.serialization.Serializable

@Serializable
public data class MovementStep(
    public val position: HexCoordinates,
    public val facing: HexDirection,
)
