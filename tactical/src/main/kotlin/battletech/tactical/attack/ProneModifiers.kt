package battletech.tactical.attack

import battletech.tactical.unit.VisibleUnit
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.CriticalComponent
import battletech.tactical.unit.CritEffect
import battletech.tactical.unit.critEffects

/**
 * To-hit modifier for striking a prone target (`docs/rules/to-hit-modifiers.md`).
 *
 * Takes a [VisibleUnit]: [VisibleUnit.isProne] is public (a prone miniature is lying on the
 * table), so this runs identically for a target the caller owns and one it doesn't.
 */
public fun proneTargetToHitModifier(target: VisibleUnit, distance: Int): Int = when {
    !target.isProne -> 0
    distance <= 1 -> -2
    else -> 1
}

/**
 * To-hit bonus for attacking an immobile (shut-down) target (`docs/rules/heat.md` §3).
 *
 * Takes a [VisibleUnit] for the same reason as [proneTargetToHitModifier]:
 * [VisibleUnit.isShutdown] is public.
 */
public fun immobileTargetToHitModifier(target: VisibleUnit): Int =
    if (target.isShutdown) -4 else 0

/**
 * To-hit penalty applied to ALL of [attacker]'s weapon attacks from sensor critical hits
 * (`docs/rules/critical-hits.md` §5). Only the penalty tier is reported here; the blinding
 * tier is enforced separately in [battletech.tactical.query.WeaponTargeting]. Derives from
 * the single tier -> effect source, [critEffects].
 */
public fun sensorToHitModifier(attacker: CombatUnit): Int =
    attacker.critEffects(CriticalComponent.SENSOR).filterIsInstance<CritEffect.ToHitPenalty>().sumOf { it.amount }
