package io.archinaut.battletech.tactical.query

import io.archinaut.battletech.tactical.attack.ToHitBreakdown
import io.archinaut.battletech.tactical.attack.physical.PhysicalAttackKind
import io.archinaut.battletech.tactical.rules.RuleRejection
import io.archinaut.battletech.tactical.unit.UnitId

/**
 * A single offerable physical attack against an adjacent enemy: the concrete
 * [kind] to submit, a display [label], whether it is currently [available],
 * its full to-hit [toHit] breakdown and expected damage, and — when unavailable — the rule
 * reasons why.
 *
 * Unlike [io.archinaut.battletech.tactical.attack.weapon.WeaponTargetInfo], [toHit] is never absent: a
 * physical attack's piloting-based math ([io.archinaut.battletech.tactical.attack.physical.physicalToHitBreakdown])
 * doesn't depend on the rule checks that make an option [available] or not, so an unavailable
 * option still carries the number resolution would have used — genuinely different math from
 * a weapon whose to-hit depends on the same range/LOS/ammo checks that gate its availability.
 */
public data class PhysicalAttackOption(
    public val targetId: UnitId,
    public val targetName: String,
    public val kind: PhysicalAttackKind,
    public val label: String,
    public val available: Boolean,
    public val toHit: ToHitBreakdown,
    public val expectedDamage: Int,
    public val unavailableReasons: List<RuleRejection>,
)
