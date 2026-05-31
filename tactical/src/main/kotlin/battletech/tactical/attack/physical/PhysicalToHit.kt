package battletech.tactical.attack.physical

import battletech.tactical.attack.heatPenaltyModifier
import battletech.tactical.model.GameState
import battletech.tactical.model.Terrain
import battletech.tactical.model.MovementMode
import battletech.tactical.unit.CombatUnit

/**
 * Probability (percent, 0..100) of rolling at least [targetNumber] on 2d6.
 * Shared by physical attack definitions for their success-chance previews.
 */
public fun twoD6AtLeastProbability(targetNumber: Int): Int = when {
    targetNumber <= 2 -> 100
    targetNumber >= 13 -> 0
    else -> TWO_D6_AT_LEAST[targetNumber] ?: 0
}

/**
 * Total Warfare physical-attack to-hit target number:
 * piloting skill + attacker movement modifier + heat penalty + the attack's
 * own modifier (kick is −2). Target movement, terrain, elevation and prone
 * modifiers are layered in by later slices.
 */
public fun physicalToHitTargetNumber(
    attacker: CombatUnit,
    target: CombatUnit,
    kind: PhysicalAttackKind,
    gameState: GameState,
): Int = attacker.pilotingSkill +
    attackerMovementModifier(attacker) +
    targetMovementModifier(target) +
    terrainModifier(target, gameState) +
    heatPenaltyModifier(attacker) +
    attackKindModifier(kind)

private fun attackerMovementModifier(attacker: CombatUnit): Int = when (attacker.movementThisTurn.mode) {
    null -> 0
    MovementMode.WALK -> 1
    MovementMode.RUN -> 2
    MovementMode.JUMP -> 3
}

private fun targetMovementModifier(target: CombatUnit): Int {
    val movement = target.movementThisTurn
    val hexBand = when (movement.hexesMoved) {
        in 0..2 -> 0
        in 3..4 -> 1
        in 5..6 -> 2
        in 7..9 -> 3
        in 10..17 -> 4
        in 18..24 -> 5
        else -> 6
    }
    val jumpBonus = if (movement.mode == MovementMode.JUMP) 1 else 0
    return hexBand + jumpBonus
}

private fun terrainModifier(target: CombatUnit, gameState: GameState): Int =
    when (gameState.map.hexes[target.position]?.terrain) {
        Terrain.LIGHT_WOODS -> 1
        Terrain.HEAVY_WOODS -> 2
        else -> 0
    }

private fun attackKindModifier(kind: PhysicalAttackKind): Int = when (kind) {
    is PhysicalAttackKind.Punch -> 0
    is PhysicalAttackKind.Kick -> -2
}

private val TWO_D6_AT_LEAST: Map<Int, Int> = mapOf(
    2 to 100,
    3 to 97,
    4 to 92,
    5 to 83,
    6 to 72,
    7 to 58,
    8 to 42,
    9 to 28,
    10 to 17,
    11 to 8,
    12 to 3,
)
