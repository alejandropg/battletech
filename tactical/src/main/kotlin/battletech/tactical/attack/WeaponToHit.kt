package battletech.tactical.attack

import battletech.tactical.model.GameState
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
 */
public fun weaponToHitModifiers(
    attacker: CombatUnit,
    target: CombatUnit,
    weapon: Weapon,
    distance: Int,
    isPrimaryTarget: Boolean,
    gameState: GameState,
): List<ToHitModifier> {
    val band = rangeBandFor(distance, weapon)
    val los = lineOfSight(attacker, target, gameState.map)
    val terrainMod = los.woodsModifier + if (los.partialCover) 3 else 0
    return listOf(
        ToHitModifier(rangeLabelFor(band), rangeModifierFor(band)),
        ToHitModifier("heat", heatPenaltyModifier(attacker)),
        ToHitModifier("secondary", if (!isPrimaryTarget) 1 else 0),
        ToHitModifier("prone", proneTargetToHitModifier(target, distance)),
        ToHitModifier("immobile", immobileTargetToHitModifier(target)),
        ToHitModifier("sensors", sensorToHitModifier(attacker)),
        ToHitModifier("attacker move", attackerMovementModifier(attacker.movementThisTurn)),
        ToHitModifier("target move", targetMovementModifier(target.movementThisTurn)),
        ToHitModifier("min range", minimumRangeModifier(distance, weapon.minimumRange)),
        ToHitModifier("terrain", terrainMod),
    )
}

private fun minimumRangeModifier(distance: Int, minimumRange: Int): Int =
    if (distance < minimumRange) minimumRange - distance + 1 else 0
