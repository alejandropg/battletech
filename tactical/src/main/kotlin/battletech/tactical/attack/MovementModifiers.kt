package battletech.tactical.attack

import battletech.tactical.model.MovementMode
import battletech.tactical.unit.MovementThisTurn

/**
 * Returns the to-hit penalty for the attacker's movement this turn.
 * Stationary → +0, walk → +1, run → +2, jump → +3.
 * Used by both weapon fire and physical attacks.
 */
public fun attackerMovementModifier(movement: MovementThisTurn): Int = when (movement) {
    is MovementThisTurn.Stationary -> 0
    is MovementThisTurn.Moved -> when (movement.mode) {
        MovementMode.WALK -> 1
        MovementMode.RUN -> 2
        MovementMode.JUMP -> 3
    }
}

/**
 * Returns the Target Movement Modifier (TMM) for the given target's movement.
 * The base band is keyed on hexes moved; a jumping target adds +1 on top.
 * Used by both weapon fire and physical attacks.
 */
public fun targetMovementModifier(movement: MovementThisTurn): Int {
    val hexBand = when (movement.hexesMoved) {
        in 0..2 -> 0
        in 3..4 -> 1
        in 5..6 -> 2
        in 7..9 -> 3
        in 10..17 -> 4
        in 18..24 -> 5
        else -> 6
    }
    val jumpBonus = if (movement is MovementThisTurn.Moved && movement.mode == MovementMode.JUMP) 1 else 0
    return hexBand + jumpBonus
}
