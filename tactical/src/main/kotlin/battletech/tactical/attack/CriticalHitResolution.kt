package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.MechLocation
import battletech.tactical.session.AmmoExploded
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.GameEvent
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.SLOT_COUNTS
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.WeaponMountId
import battletech.tactical.unit.isSlotDestroyed
import battletech.tactical.unit.withSlot

private val ARM_OR_LEG_LOCATIONS: Set<MechLocation> = setOf(
    MechLocation.LEFT_ARM,
    MechLocation.RIGHT_ARM,
    MechLocation.LEFT_LEG,
    MechLocation.RIGHT_LEG,
)

/**
 * Number of components destroyed by a 2d6 crit-check roll of [total] at [location],
 * per the Critical Hit Table (`docs/rules/armor-damage.md` §3): 2-7 none, 8-9 one,
 * 10-11 two, 12 three (Head/Torso) — or a limb blow-off on Arm/Leg, represented here
 * as a sentinel of 6 crits (more than any location's slot count) so callers can detect
 * it via [isLimbBlownOff] without a separate enum.
 */
private fun critCountFor(total: Int, location: MechLocation): Int = when (total) {
    in 2..7 -> 0
    8, 9 -> 1
    10, 11 -> 2
    12 -> if (location in ARM_OR_LEG_LOCATIONS) Int.MAX_VALUE else 3
    else -> error("invalid 2d6 total: $total")
}

private fun isLimbBlownOff(total: Int, location: MechLocation): Boolean =
    total == 12 && location in ARM_OR_LEG_LOCATIONS

/** A single slot picked by the 1d6 block + 1d6 slot crit-location dice, with roll-again applied. */
private data class SlotPick(val index: Int, val content: CriticalSlotContent)

/**
 * Picks one valid (non-empty, not-already-destroyed) slot in [location] using the
 * doc's dice procedure: 1d6 for block (1-3 upper, 4-6 lower; only meaningful for
 * 12-slot locations — 6-slot locations have a single block), 1d6 for the slot
 * (1-6) within that block. Rolls again (consuming fresh dice, in order) whenever
 * the chosen slot is [CriticalSlotContent.Empty] or already destroyed on [unit].
 *
 * Returns null if [location] has no remaining valid slot to hit (e.g. an empty
 * side torso) — callers must not loop forever rolling against such a location.
 */
private fun pickSlot(unit: CombatUnit, location: MechLocation, roller: DiceRoller): SlotPick? {
    val slots = unit.criticalLayout.slotsAt(location)
    val slotCount = SLOT_COUNTS.getValue(location)
    val hasValidSlot = slots.indices.any { index ->
        slots[index] != CriticalSlotContent.Empty && !unit.isSlotDestroyed(location, index)
    }
    if (!hasValidSlot) return null

    while (true) {
        val blockRoll = roller.d6()
        val slotRoll = roller.d6()
        val blockStart = if (blockRoll <= 3) 0 else 6
        val index = blockStart + (slotRoll - 1)
        if (index >= slotCount) continue
        val content = slots[index]
        if (content == CriticalSlotContent.Empty) continue
        if (unit.isSlotDestroyed(location, index)) continue
        return SlotPick(index, content)
    }
}

/** Marks the slot at [location]/[index] destroyed in [unit]'s [CombatUnit.criticalHits]. */
private fun CombatUnit.withSlotDestroyed(location: MechLocation, index: Int): CombatUnit {
    val existing = criticalHits[location] ?: emptySet()
    return copy(criticalHits = criticalHits + (location to (existing + index)))
}

/**
 * Detonates the ammo bin at [binLocation]/[slotIndex] on [unit]: the total damage of
 * [bin]'s remaining shots (`shots × type.damagePerShot`, `docs/rules/armor-damage.md`
 * §3 "Ammunition (Ammo Explosion)") is applied through the standard [applyDamage]
 * path to [damageLocation] — the bin's own location for a critical-hit detonation, or
 * `CENTER_TORSO` for the heat-phase cook-off ([battletech.tactical.session.HeatPhaseHandler])
 * — and the bin is emptied (`shots = 0`). A bin with no shots left does nothing.
 *
 * Pure and deterministic: the damage is a fixed function of the bin's contents, no
 * dice involved. Shared by crit-triggered and heat-triggered ammo explosions so both
 * paths stay in sync.
 */
public fun detonateAmmoBin(
    unit: CombatUnit,
    binLocation: MechLocation,
    slotIndex: Int,
    bin: CriticalSlotContent.AmmoBin,
    damageLocation: MechLocation = binLocation,
): Pair<CombatUnit, AmmoExploded?> {
    if (bin.shots <= 0) return unit to null

    val damage = bin.shots * bin.type.damagePerShot
    val emptiedLayout = unit.criticalLayout.withSlot(binLocation, slotIndex, bin.copy(shots = 0))
    val damaged = applyDamage(unit.copy(criticalLayout = emptiedLayout), damageLocation, damage)
    return damaged to AmmoExploded(unit.id, bin.type, damage)
}

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
            if (weapon.mountId != null && weapon.mountId in weaponIds) weapon.copy(destroyed = true)
            else weapon
        },
    )
}

/**
 * Applies the per-component consequence of destroying [content] at [location] on
 * [unit] (`docs/rules/armor-damage.md` §3): a weapon-mount slot disables that
 * [Weapon] (`destroyed = true`); an ammo-bin slot detonates the bin into its own
 * location via [detonateAmmoBin]. Any other content (structure, actuators, engine,
 * gyro, …) has no Stage 4 consequence yet. Returns the updated unit and the
 * [AmmoExploded] event, if a detonation occurred.
 */
private fun CombatUnit.applyCritConsequence(
    location: MechLocation,
    slotIndex: Int,
    content: CriticalSlotContent,
): Pair<CombatUnit, AmmoExploded?> = when (content) {
    is CriticalSlotContent.WeaponMount -> withWeaponDestroyed(content.weaponId) to null
    is CriticalSlotContent.AmmoBin -> detonateAmmoBin(this, location, slotIndex, content)
    else -> this to null
}

/** Marks every slot in [location] destroyed and zeroes its internal structure (limb blow-off). */
private fun CombatUnit.withLimbBlownOff(location: MechLocation): CombatUnit {
    val slotCount = SLOT_COUNTS.getValue(location)
    val allIndices = (0 until slotCount).toSet()
    val newInternalStructure = setInternalStructure(internalStructure, location, 0)
    return copy(
        criticalHits = criticalHits + (location to allIndices),
        internalStructure = newInternalStructure,
    )
}

/**
 * Resolves a single crit *check* at [location] on [unit]: rolls 2d6 on the Critical
 * Hit Table, then resolves each scored crit by picking a slot (1d6 block + 1d6 slot,
 * with roll-again) and marking it destroyed. A roll of 12 on an Arm/Leg blows the
 * limb off instead — every slot in [location] is marked destroyed and its internal
 * structure zeroed (Stage 1/2's `destructionReason`/sweep then handle elimination).
 *
 * Each newly-destroyed slot's per-component consequence is applied immediately
 * (`docs/rules/armor-damage.md` §3): a weapon-mount slot disables that weapon; an
 * ammo-bin slot detonates ([detonateAmmoBin]) into its own location, which can itself
 * cause further IS damage (caught by the session's later destruction sweep). An ammo
 * explosion also inflicts 2 pilot hits on the unit (each hit runs a consciousness check
 * 2d6 via [applyPilotHit], immediately after the [AmmoExploded] event).
 *
 * **Canonical dice order per crit slot** (for seeded tests):
 *  - 2d6 crit table, then per scored crit: 1d6 block + 1d6 slot (with roll-again).
 *  - For an ammo-bin slot: after [AmmoExploded], consciousness check 2d6 (hit 1),
 *    then consciousness check 2d6 (hit 2).
 *
 * Returns the fully-updated [CombatUnit] alongside the [GameEvent]s produced —
 * one [CriticalHit] per destroyed slot, [AmmoExploded] and [battletech.tactical.session.PilotHit]
 * events per ammo detonation — in slot-resolution order. All dice flow through [roller];
 * no raw `Random`.
 */
public fun resolveCriticalHits(
    unit: CombatUnit,
    location: MechLocation,
    roller: DiceRoller,
): Pair<CombatUnit, List<GameEvent>> {
    val critRoll = roller.roll2d6()
    val total = critRoll.total

    if (isLimbBlownOff(total, location)) {
        val slots = unit.criticalLayout.slotsAt(location)
        var currentUnit = unit
        val events = mutableListOf<GameEvent>()
        for (index in slots.indices) {
            if (currentUnit.isSlotDestroyed(location, index)) continue
            val content = slots[index]
            events += CriticalHit(currentUnit.id, location, index, content)
            val (afterConsequence, ammoEvent) = currentUnit.applyCritConsequence(location, index, content)
            currentUnit = afterConsequence
            ammoEvent?.let {
                events += it
                // Ammo explosion: pilot takes 2 hits (standard BT rule). Each hit runs a
                // consciousness check via applyPilotHit; dice order: hit-1 2d6, hit-2 2d6.
                repeat(2) {
                    val (afterHit, hitEvents) = applyPilotHit(currentUnit, roller)
                    currentUnit = afterHit
                    events += hitEvents
                }
            }
        }
        return currentUnit.withLimbBlownOff(location) to events
    }

    val critCount = critCountFor(total, location)
    if (critCount == 0) return unit to emptyList()

    var currentUnit = unit
    val events = mutableListOf<GameEvent>()
    repeat(critCount) {
        val pick = pickSlot(currentUnit, location, roller) ?: return@repeat
        currentUnit = currentUnit.withSlotDestroyed(location, pick.index)
        events += CriticalHit(currentUnit.id, location, pick.index, pick.content)
        val (afterConsequence, ammoEvent) = currentUnit.applyCritConsequence(location, pick.index, pick.content)
        currentUnit = afterConsequence
        ammoEvent?.let {
            events += it
            // Ammo explosion: pilot takes 2 hits (standard BT rule). Each hit runs a
            // consciousness check via applyPilotHit; dice order: hit-1 2d6, hit-2 2d6.
            repeat(2) {
                val (afterHit, hitEvents) = applyPilotHit(currentUnit, roller)
                currentUnit = afterHit
                events += hitEvents
            }
        }
    }
    return currentUnit to events
}
