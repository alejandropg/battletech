package battletech.tactical.attack.physical

import battletech.tactical.attack.ToHitModifier
import battletech.tactical.attack.heatPenaltyModifier
import battletech.tactical.attack.proneTargetToHitModifier
import battletech.tactical.attack.total
import battletech.tactical.model.GameState
import battletech.tactical.model.Terrain
import battletech.tactical.model.MovementMode
import battletech.tactical.unit.CombatUnit

/**
 * Ordered breakdown of every contribution to a physical attack's to-hit target
 * number, excluding the attacker's piloting skill (the base, added separately
 * like gunnery is for weapons): attacker movement, target movement, terrain,
 * prone target, heat penalty, and the attack kind's own modifier (kick is −2).
 */
public fun physicalToHitModifiers(
    attacker: CombatUnit,
    target: CombatUnit,
    kind: PhysicalAttackKind,
    gameState: GameState,
): List<ToHitModifier> = listOf(
    ToHitModifier("attacker move", attackerMovementModifier(attacker)),
    ToHitModifier("target move", targetMovementModifier(target)),
    ToHitModifier("terrain", terrainModifier(target, gameState)),
    ToHitModifier("prone", proneTargetToHitModifier(target, attacker.position.distanceTo(target.position))),
    ToHitModifier("heat", heatPenaltyModifier(attacker)),
    ToHitModifier(attackKindLabel(kind), attackKindModifier(kind)),
)

/**
 * Total Warfare physical-attack to-hit target number:
 * piloting skill + [physicalToHitModifiers].
 */
public fun physicalToHitTargetNumber(
    attacker: CombatUnit,
    target: CombatUnit,
    kind: PhysicalAttackKind,
    gameState: GameState,
): Int = attacker.pilotingSkill + physicalToHitModifiers(attacker, target, kind, gameState).total()

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

private fun attackKindLabel(kind: PhysicalAttackKind): String = when (kind) {
    is PhysicalAttackKind.Punch -> "punch"
    is PhysicalAttackKind.Kick -> "kick"
}

