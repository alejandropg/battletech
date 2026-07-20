package battletech.tactical.unit

import battletech.tactical.model.MovementMode
import kotlinx.serialization.Serializable

/**
 * How a unit moved during the current turn's movement phase, used to compute
 * attacker movement modifiers and the target movement modifier (TMM) in the
 * attack phases.
 *
 * [Stationary] means either the unit never submitted a [battletech.tactical.session.MoveUnit]
 * command this turn (e.g. it starts each movement phase reset to [Stationary]), or it did
 * submit one but spent 0 MP — a genuine "stay put" declaration (same hex, same facing). Per
 * the tabletop rules, a stationary attacker is +0.
 *
 * A unit that submitted [battletech.tactical.session.MoveUnit] and spent *at least 1 MP* —
 * including a turn-in-place that only changes facing and enters no new hex — is [Moved] with
 * `hexesMoved = 0`; that distinction matters because the attacker-movement +1/+2 to-hit
 * modifier applies to any [Moved], including a 0-hex turn-in-place.
 */
@Serializable
public sealed interface MovementThisTurn {
    public val hexesMoved: Int

    @Serializable
    public data object Stationary : MovementThisTurn {
        override val hexesMoved: Int get() = 0
    }

    @Serializable
    public data class Moved(
        public val mode: MovementMode,
        override val hexesMoved: Int,
    ) : MovementThisTurn
}
