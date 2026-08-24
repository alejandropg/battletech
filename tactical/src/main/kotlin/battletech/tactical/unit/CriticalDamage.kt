package battletech.tactical.unit

import battletech.tactical.model.MechLocation

/**
 * [CombatUnit] critical-damage behavior: slot/component crit counts, the derived
 * per-component [CritEffect]s ([criticalEffects] itself — the tier -> effect mapping —
 * stays in `CriticalEffects.kt`, alongside the [CritEffect] type it produces), leg
 * destruction, and the unit-mutation helpers [resolveCriticalHits][battletech.tactical.attack.resolveCriticalHits]
 * applies as it resolves a crit check. Gathered here (rather than split across
 * `CriticalLayout`/`CriticalEffects`/`LegDestruction`) because callers reasoning about
 * "what does this unit's damage state mean" want one place to look; `CriticalLayout.kt`
 * keeps the pure layout (slots, framework, ammo bookkeeping) concerns.
 */

/** True when [unit] has already recorded the slot at [location]/[index] as destroyed. */
public fun CombatUnit.isSlotDestroyed(location: MechLocation, index: Int): Boolean =
    criticalHits[location]?.contains(index) == true

/**
 * Count of slots in [location] whose content matches [predicate] and are recorded as
 * destroyed in [CombatUnit.criticalHits]. Backs the per-component counts below, which
 * compare against the threshold constants in `CriticalLayout.kt`.
 */
public fun CombatUnit.destroyedSlotCount(
    location: MechLocation,
    predicate: (CriticalSlotContent) -> Boolean,
): Int {
    val destroyedIndices = criticalHits[location] ?: return 0
    val slots = criticalLayout.slotsAt(location)
    return destroyedIndices.count { index -> slots.getOrNull(index)?.let(predicate) == true }
}

/** Number of destroyed Engine slots in the Center Torso (`docs/rules/critical-hits.md` §3). */
public fun CombatUnit.engineCritCount(): Int =
    destroyedSlotCount(MechLocation.CENTER_TORSO) { it is CriticalSlotContent.Engine }

/** Number of destroyed Gyro slots in the Center Torso (`docs/rules/critical-hits.md` §3). */
public fun CombatUnit.gyroCritCount(): Int =
    destroyedSlotCount(MechLocation.CENTER_TORSO) { it is CriticalSlotContent.Gyro }

/** Number of destroyed Sensors slots in the Head (`docs/rules/critical-hits.md` §3). */
public fun CombatUnit.sensorCritCount(): Int =
    destroyedSlotCount(MechLocation.HEAD) { it is CriticalSlotContent.Sensors }

/**
 * Number of destroyed Life Support slots in the Head (`docs/rules/critical-hits.md` §3).
 * Drives the per-turn pilot-damage sources wired in
 * [battletech.tactical.heat.resolveUnitHeatPhase].
 */
public fun CombatUnit.lifeSupportCritCount(): Int =
    destroyedSlotCount(MechLocation.HEAD) { it is CriticalSlotContent.LifeSupport }

/** Convenience: effects for [component] given this unit's current crit count for it. */
public fun CombatUnit.critEffects(component: CriticalComponent): List<CritEffect> {
    val hits = when (component) {
        CriticalComponent.ENGINE -> engineCritCount()
        CriticalComponent.GYRO -> gyroCritCount()
        CriticalComponent.SENSOR -> sensorCritCount()
        CriticalComponent.LIFE_SUPPORT -> lifeSupportCritCount()
    }
    return criticalEffects(component, hits)
}

/** Sum of this unit's active engine [CritEffect.HeatPerTurn] amounts (0 when no engine crits). */
public fun CombatUnit.engineHeatPerTurn(): Int =
    critEffects(CriticalComponent.ENGINE).filterIsInstance<CritEffect.HeatPerTurn>().sumOf { it.amount }

/**
 * [engineHeatPerTurn] as a labelled [HeatSource], or null when there is none. Unlike the sources
 * in [CombatUnit.heatGeneratedThisTurn] (accumulated as movement/weapon fire is committed, then
 * cleared each Heat Phase), this is *derived* fresh from the unit's current crits every time it is
 * read — never appended to [CombatUnit.heatGeneratedThisTurn] itself, which would double-count it
 * on the next fold. See [battletech.tactical.heat.projectHeat], the sole caller.
 */
public fun CombatUnit.engineHeatSource(): HeatSource? =
    engineHeatPerTurn().takeIf { it > 0 }?.let { HeatSource("Engine damage", it) }

/** True when sensor damage has blinded this unit ([CritEffect.CannotFire] tier reached). */
public fun CombatUnit.cannotFireFromSensorDamage(): Boolean =
    critEffects(CriticalComponent.SENSOR).any { it is CritEffect.CannotFire }

/** True when gyro damage prevents this unit from standing ([CritEffect.CannotStand] tier reached). */
public fun CombatUnit.cannotStandFromGyroDamage(): Boolean =
    critEffects(CriticalComponent.GYRO).any { it is CritEffect.CannotStand }

/**
 * Number of legs whose internal structure has reached 0 on this unit (0, 1, or 2).
 * What that costs the unit is owned by `docs/rules/armor-damage.md` §8; both legs
 * destroyed triggers [DestructionReason.BOTH_LEGS_DESTROYED] via [destructionReason].
 */
public fun CombatUnit.destroyedLegCount(): Int {
    var count = 0
    if (!internalStructure.isIntact(MechLocation.LEFT_LEG)) count++
    if (!internalStructure.isIntact(MechLocation.RIGHT_LEG)) count++
    return count
}

/** PSR modifier per destroyed leg (`docs/rules/pilot.md` §3). */
public const val LEG_PSR_PENALTY: Int = 1

/**
 * PSR modifier applied to all piloting skill rolls while [unit] has at least one
 * destroyed leg, mirroring the [gyroPsrModifier] pattern. Consumed by
 * [battletech.tactical.movement.MovementPhaseHandler] (stand-up attempts) and by the
 * forced-PSR path.
 */
public fun legPsrModifier(unit: CombatUnit): Int = unit.destroyedLegCount() * LEG_PSR_PENALTY

// Unit-mutation helpers used while resolving a critical hit. They only touch
// CombatUnit/unit-package types; detonateAmmoBin and applyCritConsequence stay in
// attack/ since they run the damage pipeline.

/** Sets `destroyed = true` on the unit's [Weapon] mounted at [weaponId], if any. */
internal fun CombatUnit.withWeaponDestroyed(weaponId: WeaponMountId): CombatUnit =
    copy(
        weapons = weapons.map { weapon ->
            if (weapon.mountId == weaponId) weapon.copy(destroyed = true) else weapon
        },
    )

/**
 * Marks every weapon mounted in [location] as destroyed (`destroyed = true`). Used by
 * both the crit-driven limb-blow-off path and the raw-damage location-destruction path
 * so both share a single implementation. Idempotent — already-destroyed weapons are
 * unchanged. Weapons in other locations are unaffected.
 */
internal fun CombatUnit.disableWeaponsIn(location: MechLocation): CombatUnit {
    val weaponIds = criticalLayout.weaponIdsAt(location)
    if (weaponIds.isEmpty()) return this
    return copy(
        weapons = weapons.map { weapon ->
            if (weapon.mountId in weaponIds) weapon.copy(destroyed = true) else weapon
        },
    )
}

/** Marks every slot in [location] destroyed and zeroes its internal structure (limb blow-off). */
internal fun CombatUnit.withLimbBlownOff(location: MechLocation): CombatUnit {
    val slotCount = SLOT_COUNTS.getValue(location)
    val allIndices = (0 until slotCount).toSet()
    val newInternalStructure = internalStructure.with(location, 0)
    return copy(
        criticalHits = criticalHits + (location to allIndices),
        internalStructure = newInternalStructure,
    )
}

/** Marks the slot at [location]/[index] destroyed in [unit]'s [CombatUnit.criticalHits]. */
internal fun CombatUnit.withSlotDestroyed(location: MechLocation, index: Int): CombatUnit {
    val existing = criticalHits[location] ?: emptySet()
    return copy(criticalHits = criticalHits + (location to (existing + index)))
}
