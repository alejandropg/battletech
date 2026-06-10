package battletech.tactical.attack

import battletech.tactical.unit.CombatUnit

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
