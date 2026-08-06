package battletech.tactical.unit

import battletech.tactical.model.MechLocation

/**
 * Reasons a [CombatUnit] is eliminated from play (`docs/rules/armor-damage.md` §7).
 *
 * Note: a destroyed gyro is deliberately absent — it immobilizes rather than eliminates
 * (`docs/rules/critical-hits.md` §5), so that handling lives in the attack phase handlers
 * and [battletech.tactical.movement.MovementPhaseHandler], not here.
 */
public enum class DestructionReason {
    HEAD_DESTROYED,
    CENTER_TORSO_DESTROYED,
    BOTH_LEGS_DESTROYED,
    ENGINE_DESTROYED,
    PILOT_DEAD,
}

/** Pilot hit count at which the pilot dies (`docs/rules/pilot.md` §1). */
public const val PILOT_DEATH_THRESHOLD: Int = 6

/**
 * Pure query: returns the first destruction condition satisfied by [unit], or null if
 * the unit is still intact. Does not mutate [unit].
 *
 * The conditions themselves are mutually exclusive in practice, but the first match wins
 * and the order still matters for a caller reading the reason off a unit that satisfies
 * more than one at once. Structural reasons are checked first, then ENGINE_DESTROYED,
 * then PILOT_DEAD last. ASSUMPTION: the doc does not rank them. If a single damage event
 * satisfies both a structural condition and pilot death (a head hit that zeroes head IS
 * *and* is the pilot's last hit), the more physical reason is the more debuggable label.
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
