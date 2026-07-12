package battletech.tactical.unit

import battletech.tactical.model.MovementMode
import kotlinx.serialization.Serializable

/**
 * How a unit moved during the current turn's movement phase, used to compute
 * attacker movement modifiers and the target movement modifier (TMM) in the
 * attack phases. [mode] is null when the unit stayed stationary.
 */
@Serializable
public data class MovementThisTurn(
    public val mode: MovementMode?,
    public val hexesMoved: Int,
) {
    public companion object {
        public val STATIONARY: MovementThisTurn = MovementThisTurn(mode = null, hexesMoved = 0)
    }
}
