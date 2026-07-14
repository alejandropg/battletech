package battletech.tactical.unit

import battletech.tactical.model.MechLocation

/**
 * Reasons a [CombatUnit] is eliminated from play (`docs/rules/armor-damage.md` §5).
 *
 * Note: a destroyed gyro is NOT a destruction reason — per the rules doc §3 a 2nd gyro
 * crit immobilizes the mech (it crashes prone and can never stand) but it keeps fighting
 * from the ground, so it stays in play. That handling lives in the attack phase handlers
 * and [battletech.tactical.movement.MovementPhaseHandler], not here.
 */
public enum class DestructionReason {
    HEAD_DESTROYED,
    CENTER_TORSO_DESTROYED,
    BOTH_LEGS_DESTROYED,
    ENGINE_DESTROYED,
    PILOT_DEAD,
}

/**
 * Pilot hit count at which the pilot dies (ASSUMPTION — not stated in
 * `docs/rules/armor-damage.md`; standard BattleTech rule: 6 pilot hits kills the
 * MechWarrior outright, mirroring the 6-box "Hits Sustained" track on a paper pilot
 * sheet).
 */
public const val PILOT_DEATH_THRESHOLD: Int = 6

/**
 * Pure query: returns the first destruction condition satisfied by [unit], or null if
 * the unit is still intact. Does not mutate [unit].
 *
 * Precedence (checked in this order; the first match wins — these conditions are
 * mutually exclusive in practice since each consumes a unit's last sliver of viability,
 * but the order is still meaningful for callers reading the reason off a unit that
 * satisfies more than one at once):
 *  1. Structural (head / CT IS = 0, both legs gone) — already-final, simplest to verify.
 *  2. ENGINE_DESTROYED (3 engine crits) — an immediate shutdown per the doc.
 *  3. PILOT_DEAD (6 pilot hits) — checked LAST (ASSUMPTION). A dead pilot's mech is
 *     just as eliminated as one with a destroyed head, but if a single damage event
 *     somehow satisfies both a structural/crit condition and pilot death at once (e.g.
 *     a head hit that both zeroes head IS and is the pilot's 6th hit), the more
 *     "physical" reason is reported — it is the more informative/debuggable label,
 *     and matches the order new conditions were added to this function over the
 *     project's staged build-out.
 */
public fun destructionReason(unit: CombatUnit): DestructionReason? {
    val internalStructure = unit.internalStructure
    return when {
        !internalStructure.isIntact(MechLocation.HEAD) -> DestructionReason.HEAD_DESTROYED
        !internalStructure.isIntact(MechLocation.CENTER_TORSO) -> DestructionReason.CENTER_TORSO_DESTROYED
        !internalStructure.isIntact(MechLocation.LEFT_LEG) &&
            !internalStructure.isIntact(MechLocation.RIGHT_LEG) -> DestructionReason.BOTH_LEGS_DESTROYED
        unit.engineCritCount() >= ENGINE_DESTROYED_AT -> DestructionReason.ENGINE_DESTROYED
        unit.pilotHits >= PILOT_DEATH_THRESHOLD -> DestructionReason.PILOT_DEAD
        else -> null
    }
}
