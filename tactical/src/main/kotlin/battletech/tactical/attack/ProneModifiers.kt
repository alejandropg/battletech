package battletech.tactical.attack

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.CriticalComponent
import battletech.tactical.unit.CritEffect
import battletech.tactical.unit.critEffects

/**
 * To-hit modifier for striking a prone target: a prone 'Mech is easier to hit
 * in an adjacent hex (−2) but harder to hit at range (+1). No modifier when
 * the target is standing.
 */
public fun proneTargetToHitModifier(target: CombatUnit, distance: Int): Int = when {
    !target.isProne -> 0
    distance <= 1 -> -2
    else -> 1
}

/**
 * To-hit bonus for attacking an immobile (shut-down) target: it cannot evade,
 * so every attacker enjoys −4. No modifier against an operational target.
 */
public fun immobileTargetToHitModifier(target: CombatUnit): Int =
    if (target.isShutdown) -4 else 0

/**
 * +2 to-hit penalty applied to ALL of [attacker]'s weapon attacks once it has
 * taken its first sensor critical hit (`docs/rules/armor-damage.md` §3 Quick
 * Reference table). A second sensor crit blinds the unit entirely (it cannot
 * fire at all — enforced separately in [battletech.tactical.query.WeaponTargeting]),
 * so this modifier only ever needs to report the single +2 tier. Derives from the
 * single tier -> effect source, [critEffects].
 */
public fun sensorToHitModifier(attacker: CombatUnit): Int =
    attacker.critEffects(CriticalComponent.SENSOR).filterIsInstance<CritEffect.ToHitPenalty>().sumOf { it.amount }
