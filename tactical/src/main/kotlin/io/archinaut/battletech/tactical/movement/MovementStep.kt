package io.archinaut.battletech.tactical.movement

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.HexDirection
import kotlinx.serialization.Serializable

@Serializable
public data class MovementStep(
    public val position: HexCoordinates,
    public val facing: HexDirection,
)
