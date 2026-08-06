package battletech.tactical.attack

import battletech.tactical.model.MovementMode
import battletech.tactical.unit.MovementThisTurn

/**
 * Attacker Movement Modifier (`docs/rules/to-hit-modifiers.md` §2).
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
 * Target Movement Modifier (`docs/rules/to-hit-modifiers.md` §1).
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
