package battletech.tactical.attack.physical

import battletech.tactical.attack.ToHitModifier
import battletech.tactical.attack.attackerMovementModifier
import battletech.tactical.attack.heatPenaltyModifier
import battletech.tactical.attack.lineOfSight
import battletech.tactical.attack.proneTargetToHitModifier
import battletech.tactical.attack.targetMovementModifier
import battletech.tactical.attack.total
import battletech.tactical.model.GameState
import battletech.tactical.unit.CombatUnit

/**
 * Ordered breakdown of every contribution to a physical attack's to-hit target
 * number, excluding the attacker's piloting skill (the base, added separately
 * like gunnery is for weapons): attacker movement, target movement, terrain,
 * prone target, heat penalty, and the attack kind's own modifier (kick is −2).
 *
 * The **terrain** term uses the same [lineOfSight] routine as weapon attacks:
 * intervening woods levels + target-hex woods + partial-cover (+3) share one
 * implementation.
 */
public fun physicalToHitModifiers(
    attacker: CombatUnit,
    target: CombatUnit,
    kind: PhysicalAttackKind,
    gameState: GameState,
): List<ToHitModifier> = listOf(
    ToHitModifier("attacker move", attackerMovementModifier(attacker.movementThisTurn)),
    ToHitModifier("target move", targetMovementModifier(target.movementThisTurn)),
    ToHitModifier("terrain", terrainModifier(attacker, target, gameState)),
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

private fun terrainModifier(attacker: CombatUnit, target: CombatUnit, gameState: GameState): Int {
    val los = lineOfSight(attacker, target, gameState.map)
    return los.woodsModifier + if (los.partialCover) 3 else 0
}

private fun attackKindModifier(kind: PhysicalAttackKind): Int = when (kind) {
    is PhysicalAttackKind.Punch -> 0
    is PhysicalAttackKind.Kick -> -2
}

private fun attackKindLabel(kind: PhysicalAttackKind): String = when (kind) {
    is PhysicalAttackKind.Punch -> "punch"
    is PhysicalAttackKind.Kick -> "kick"
}
