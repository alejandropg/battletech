package battletech.tactical.attack.physical

import battletech.tactical.attack.ToHitBase
import battletech.tactical.attack.ToHitBreakdown
import battletech.tactical.attack.ToHitFactor
import battletech.tactical.attack.ToHitModifier
import battletech.tactical.attack.attackerMovementModifier
import battletech.tactical.attack.heatPenaltyModifier
import battletech.tactical.attack.lineOfSight
import battletech.tactical.attack.proneTargetToHitModifier
import battletech.tactical.attack.targetMovementModifier
import battletech.tactical.attack.total
import battletech.tactical.model.GameMap
import battletech.tactical.unit.VisibleUnit
import battletech.tactical.unit.CombatUnit

/**
 * Ordered breakdown of every contribution to a physical attack's to-hit target number
 * (`docs/rules/physical-attacks.md` §4), excluding the attacker's piloting skill — the
 * base, added separately like gunnery is for weapons.
 *
 * The terrain term uses the same [lineOfSight] routine as weapon attacks, so intervening
 * woods, target-hex woods and partial cover share one implementation.
 */
public fun physicalToHitModifiers(
    attacker: CombatUnit,
    target: VisibleUnit,
    kind: PhysicalAttackKind,
    map: GameMap,
): List<ToHitModifier> = listOf(
    ToHitModifier(ToHitFactor.ATTACKER_MOVEMENT, "attacker move", attackerMovementModifier(attacker.movementThisTurn)),
    ToHitModifier(ToHitFactor.TARGET_MOVEMENT, "target move", targetMovementModifier(target.movementThisTurn)),
    ToHitModifier(ToHitFactor.TERRAIN, "terrain", terrainModifier(attacker, target, map)),
    ToHitModifier(
        ToHitFactor.PRONE_TARGET,
        "prone",
        proneTargetToHitModifier(target, attacker.position.distanceTo(target.position)),
    ),
    ToHitModifier(ToHitFactor.HEAT, "heat", heatPenaltyModifier(attacker)),
    ToHitModifier(ToHitFactor.ATTACK_KIND, attackKindLabel(kind), attackKindModifier(kind)),
)

/** Physical-attack to-hit breakdown: piloting skill + [physicalToHitModifiers]. */
public fun physicalToHitBreakdown(
    attacker: CombatUnit,
    target: VisibleUnit,
    kind: PhysicalAttackKind,
    map: GameMap,
): ToHitBreakdown = ToHitBreakdown(ToHitBase.PILOTING, attacker.pilotingSkill, physicalToHitModifiers(attacker, target, kind, map))

/** [physicalToHitBreakdown]'s target number, for callers that don't need the full breakdown. */
public fun physicalToHitTargetNumber(
    attacker: CombatUnit,
    target: VisibleUnit,
    kind: PhysicalAttackKind,
    map: GameMap,
): Int = physicalToHitBreakdown(attacker, target, kind, map).targetNumber

private fun terrainModifier(attacker: CombatUnit, target: VisibleUnit, map: GameMap): Int {
    val los = lineOfSight(attacker.position, target.position, map)
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
