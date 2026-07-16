package battletech.tactical.attack

import battletech.tactical.model.GameMap
import battletech.tactical.unit.VisibleUnit
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.Weapon

/**
 * Resolves [distance] against [weapon]'s range brackets into the matching [RangeBand].
 * Single source of truth for the short/medium/long/out-of-range ladder so callers never
 * re-derive it from raw thresholds.
 */
public fun rangeBandFor(distance: Int, weapon: Weapon): RangeBand = when {
    distance <= weapon.shortRange -> RangeBand.SHORT
    distance <= weapon.mediumRange -> RangeBand.MEDIUM
    distance <= weapon.longRange -> RangeBand.LONG
    else -> RangeBand.OUT_OF_RANGE
}

private fun rangeModifierFor(band: RangeBand): Int = when (band) {
    RangeBand.SHORT -> 0
    RangeBand.MEDIUM -> 2
    RangeBand.LONG -> 4
    RangeBand.OUT_OF_RANGE -> 99
}

private fun rangeLabelFor(band: RangeBand): String = when (band) {
    RangeBand.SHORT -> "short"
    RangeBand.MEDIUM -> "med"
    RangeBand.LONG -> "long"
    RangeBand.OUT_OF_RANGE -> "out of range"
}

/**
 * Builds the canonical, ordered list of to-hit modifiers for a weapon attack by
 * [attacker] against [target] with [weapon] at [distance]. This is the single source of
 * truth for weapon-attack to-hit math — it composes existing per-concern helpers
 * ([heatPenaltyModifier], [proneTargetToHitModifier], [immobileTargetToHitModifier],
 * [sensorToHitModifier], [attackerMovementModifier], [targetMovementModifier]) rather
 * than re-deriving them, so every consumer (resolver, targeting preview,
 * fire-weapon validation) reports identical numbers.
 *
 * Always returns exactly ten entries, in this order: range, heat, secondary, prone,
 * immobile, sensors, attacker move, target move, min range, terrain — even when an
 * entry's amount is 0 — so callers needing the authoritative range entry (e.g. for
 * [RangeBand] derivation) don't need to special-case its absence. Use [nonZero] to
 * filter for display.
 *
 * The **terrain** entry combines intervening-woods levels, the target-hex woods
 * modifier, and partial-cover (+3) into a single term. When LOS is blocked entirely,
 * [lineOfSight] returns [LineOfSightResult.blocked] = true, which is enforced by
 * [battletech.tactical.attack.weapon.LineOfSightRule] before this function is reached.
 *
 * [attacker] is a full [CombatUnit] (heat and sensor criticals genuinely feed the math);
 * [target] is only a [VisibleUnit], because every field read off it here — position, prone,
 * shutdown, movement-this-turn — is public. That asymmetry is what lets the per-viewer query
 * path and the authoritative resolver share this one implementation: see [AttackContext].
 */
public fun weaponToHitModifiers(
    attacker: CombatUnit,
    target: VisibleUnit,
    weapon: Weapon,
    distance: Int,
    isPrimaryTarget: Boolean,
    map: GameMap,
): List<ToHitModifier> {
    val band = rangeBandFor(distance, weapon)
    val los = lineOfSight(attacker.position, target.position, map)
    val terrainMod = los.woodsModifier + if (los.partialCover) 3 else 0
    return listOf(
        ToHitModifier(ToHitFactor.RANGE, rangeLabelFor(band), rangeModifierFor(band)),
        ToHitModifier(ToHitFactor.HEAT, "heat", heatPenaltyModifier(attacker)),
        ToHitModifier(ToHitFactor.SECONDARY_TARGET, "secondary", if (!isPrimaryTarget) 1 else 0),
        ToHitModifier(ToHitFactor.PRONE_TARGET, "prone", proneTargetToHitModifier(target, distance)),
        ToHitModifier(ToHitFactor.IMMOBILE_TARGET, "immobile", immobileTargetToHitModifier(target)),
        ToHitModifier(ToHitFactor.SENSORS, "sensors", sensorToHitModifier(attacker)),
        ToHitModifier(ToHitFactor.ATTACKER_MOVEMENT, "attacker move", attackerMovementModifier(attacker.movementThisTurn)),
        ToHitModifier(ToHitFactor.TARGET_MOVEMENT, "target move", targetMovementModifier(target.movementThisTurn)),
        ToHitModifier(ToHitFactor.MINIMUM_RANGE, "min range", minimumRangeModifier(distance, weapon.minimumRange)),
        ToHitModifier(ToHitFactor.TERRAIN, "terrain", terrainMod),
    )
}

private fun minimumRangeModifier(distance: Int, minimumRange: Int): Int =
    if (distance < minimumRange) minimumRange - distance + 1 else 0

/**
 * Shared weapon-attack target number predictor: gunnery base + [modifiers] total, with no
 * clamp. Used by both attack resolution (never clamps) and
 * [battletech.tactical.query.WeaponTargeting.targetInfos], which applies its own
 * `.coerceAtLeast(2)` display clamp at the call site.
 */
public fun weaponTargetNumber(attacker: CombatUnit, modifiers: List<ToHitModifier>): Int =
    attacker.gunnerySkill + modifiers.total()
