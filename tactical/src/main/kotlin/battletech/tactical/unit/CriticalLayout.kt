package battletech.tactical.unit

import battletech.tactical.model.MechLocation

public val SLOT_COUNTS: Map<MechLocation, Int> = mapOf(
    MechLocation.HEAD to 6,
    MechLocation.CENTER_TORSO to 12,
    MechLocation.LEFT_TORSO to 12,
    MechLocation.RIGHT_TORSO to 12,
    MechLocation.LEFT_ARM to 12,
    MechLocation.RIGHT_ARM to 12,
    MechLocation.LEFT_LEG to 6,
    MechLocation.RIGHT_LEG to 6,
)

public data class LocationSlots(
    public val location: MechLocation,
    public val slots: List<CriticalSlotContent>,
)

public data class CriticalLayout(public val byLocation: Map<MechLocation, LocationSlots>) {
    public companion object Factory

    public fun slotsAt(location: MechLocation): List<CriticalSlotContent> =
        byLocation[location]?.slots ?: emptyList()

    public fun slotsForWeapon(weaponId: WeaponMountId): List<Pair<MechLocation, Int>> =
        byLocation.values.flatMap { locationSlots ->
            locationSlots.slots.mapIndexedNotNull { index, content ->
                if (content is CriticalSlotContent.WeaponMount && content.weaponId == weaponId) {
                    locationSlots.location to index
                } else {
                    null
                }
            }
        }

    public fun weaponIdsAt(location: MechLocation): Set<WeaponMountId> =
        slotsAt(location).filterIsInstance<CriticalSlotContent.WeaponMount>()
            .map { it.weaponId }
            .toSet()

    public fun ammoBins(): List<Triple<MechLocation, Int, CriticalSlotContent.AmmoBin>> =
        byLocation.values.flatMap { locationSlots ->
            locationSlots.slots.mapIndexedNotNull { index, content ->
                if (content is CriticalSlotContent.AmmoBin) {
                    Triple(locationSlots.location, index, content)
                } else {
                    null
                }
            }
        }
}

private val HEAD_FRAMEWORK: List<CriticalSlotContent> = listOf(
    CriticalSlotContent.LifeSupport,
    CriticalSlotContent.Sensors,
    CriticalSlotContent.Cockpit,
    CriticalSlotContent.Empty,
    CriticalSlotContent.Sensors,
    CriticalSlotContent.LifeSupport,
)

private val CENTER_TORSO_FRAMEWORK: List<CriticalSlotContent> = listOf(
    CriticalSlotContent.Engine,
    CriticalSlotContent.Engine,
    CriticalSlotContent.Engine,
    CriticalSlotContent.Gyro,
    CriticalSlotContent.Gyro,
    CriticalSlotContent.Gyro,
    CriticalSlotContent.Gyro,
    CriticalSlotContent.Engine,
    CriticalSlotContent.Engine,
    CriticalSlotContent.Engine,
)

private val ARM_LOCATIONS: Set<MechLocation> = setOf(MechLocation.LEFT_ARM, MechLocation.RIGHT_ARM)
private val LEG_LOCATIONS: Set<MechLocation> = setOf(MechLocation.LEFT_LEG, MechLocation.RIGHT_LEG)

private val LEG_FRAMEWORK: List<ActuatorType> = listOf(
    ActuatorType.HIP,
    ActuatorType.UPPER_LEG,
    ActuatorType.LOWER_LEG,
    ActuatorType.FOOT,
)

public fun CriticalLayout.Factory.empty(): CriticalLayout = mechLayout { }.layout

/** True when [unit] has already recorded the slot at [location]/[index] as destroyed. */
public fun CombatUnit.isSlotDestroyed(location: MechLocation, index: Int): Boolean =
    criticalHits[location]?.contains(index) == true

/**
 * Count of slots in [location] whose content matches [predicate] and are recorded as
 * destroyed in [CombatUnit.criticalHits]. Used by later stages to compare destroyed-slot
 * counts of a given component type (engine, gyro, …) against their threshold constants.
 */
public fun CombatUnit.destroyedSlotCount(
    location: MechLocation,
    predicate: (CriticalSlotContent) -> Boolean,
): Int {
    val destroyedIndices = criticalHits[location] ?: return 0
    val slots = criticalLayout.slotsAt(location)
    return destroyedIndices.count { index -> slots.getOrNull(index)?.let(predicate) == true }
}

/** Number of destroyed Engine slots in the Center Torso (`docs/rules/armor-damage.md` §3). */
public fun CombatUnit.engineCritCount(): Int =
    destroyedSlotCount(MechLocation.CENTER_TORSO) { it is CriticalSlotContent.Engine }

/** Number of destroyed Gyro slots in the Center Torso (`docs/rules/armor-damage.md` §3). */
public fun CombatUnit.gyroCritCount(): Int =
    destroyedSlotCount(MechLocation.CENTER_TORSO) { it is CriticalSlotContent.Gyro }

/** Number of destroyed Sensors slots in the Head (`docs/rules/armor-damage.md` §3). */
public fun CombatUnit.sensorCritCount(): Int =
    destroyedSlotCount(MechLocation.HEAD) { it is CriticalSlotContent.Sensors }

/**
 * Number of destroyed Life Support slots in the Head (`docs/rules/armor-damage.md`
 * §3 Life Support; HEAD framework has 2 LifeSupport slots). Drives the per-turn
 * pilot-damage sources wired in [battletech.tactical.session.HeatPhaseHandler.onEntry].
 */
public fun CombatUnit.lifeSupportCritCount(): Int =
    destroyedSlotCount(MechLocation.HEAD) { it is CriticalSlotContent.LifeSupport }

/** Engine crit count at which the mech is destroyed (`docs/rules/armor-damage.md` §3). */
public const val ENGINE_DESTROYED_AT: Int = 3

/** Gyro crit count at which the gyro is destroyed (`docs/rules/armor-damage.md` §3). */
public const val GYRO_DESTROYED_AT: Int = 2

/** Sensor crit count at which a unit's weapon attacks suffer the +2 to-hit penalty. */
public const val SENSOR_HIT_TO_HIT_PENALTY_AT: Int = 1

/** Sensor crit count at which a unit is fully blinded and cannot fire any weapons. */
public const val SENSOR_BLIND_AT: Int = 2

/** Life Support crit count at which the pilot takes a hit every turn, heat irrelevant. */
public const val LIFE_SUPPORT_FAILURE_AT: Int = 2

/** Heat generated per engine critical hit, added every turn (`docs/rules/armor-damage.md` §3). */
public const val ENGINE_CRIT_HEAT_PER_HIT: Int = 5

/** PSR modifier applied once a unit has taken at least one gyro critical hit. */
public const val GYRO_PSR_PENALTY: Int = 3

/** To-hit penalty applied to all of a unit's weapon attacks once it has taken a sensor critical hit. */
public const val SENSOR_TO_HIT_PENALTY: Int = 2

/** Standing heat at/above which a single Life Support crit causes a pilot hit this turn. */
public const val LIFE_SUPPORT_HEAT_THRESHOLD: Int = 15

/** Returns a copy of this layout with the slot at [location]/[slotIndex] replaced by [content]. */
public fun CriticalLayout.withSlot(
    location: MechLocation,
    slotIndex: Int,
    content: CriticalSlotContent,
): CriticalLayout {
    val locationSlots = byLocation.getValue(location)
    val updatedSlots = locationSlots.slots.toMutableList().also { it[slotIndex] = content }
    val updatedLocationSlots = locationSlots.copy(slots = updatedSlots)
    return copy(byLocation = byLocation + (location to updatedLocationSlots))
}

/**
 * Returns a copy of this layout with one shot consumed from the first non-empty bin of
 * [ammoType]. Bin selection mirrors [battletech.tactical.attack.weapon.HasAmmoRule]: the
 * first entry (in [ammoBins] order) whose `type == ammoType` and `shots > 0` is chosen.
 *
 * If no such bin exists (all empty or no bin of that type), this layout is returned
 * unchanged. Callers should guard with [battletech.tactical.attack.weapon.HasAmmoRule]
 * before declaration to ensure a bin is available.
 *
 * No dice involved — pure state mutation.
 */
public fun CriticalLayout.consumeOneRound(ammoType: AmmoType): CriticalLayout {
    val (location, slotIndex, bin) = ammoBins()
        .firstOrNull { (_, _, b) -> b.type == ammoType && b.shots > 0 }
        ?: return this
    return withSlot(location, slotIndex, bin.copy(shots = bin.shots - 1))
}

// ---------------------------------------------------------------------------
// IS-aware ammo helpers: exclude bins whose location has IS = 0.
// A destroyed location's feed mechanism is gone; that ammo is inaccessible.
// ---------------------------------------------------------------------------

/**
 * True when [location]'s internal structure in [is_] is still positive (the location
 * is not structurally gone). Used to filter ammo bins that are no longer accessible
 * because their location's IS has reached 0.
 */
private fun isLocationIntact(location: MechLocation, is_: InternalStructureLayout): Boolean = when (location) {
    MechLocation.HEAD -> is_.head > 0
    MechLocation.CENTER_TORSO -> is_.centerTorso > 0
    MechLocation.LEFT_TORSO -> is_.leftTorso > 0
    MechLocation.RIGHT_TORSO -> is_.rightTorso > 0
    MechLocation.LEFT_ARM -> is_.leftArm > 0
    MechLocation.RIGHT_ARM -> is_.rightArm > 0
    MechLocation.LEFT_LEG -> is_.leftLeg > 0
    MechLocation.RIGHT_LEG -> is_.rightLeg > 0
}

/**
 * Ammo bins that are available for use: bins in locations whose internal structure is
 * still positive. Bins in locations with IS = 0 are excluded because the feed mechanism
 * is inaccessible once a location is structurally destroyed.
 *
 * Use this instead of [CriticalLayout.ammoBins] everywhere availability matters
 * (weapon firing, ammo-heat cook-off, ammo consumption on fire). [CriticalLayout.ammoBins]
 * is still appropriate when inspecting the raw layout contents (tests, build validation).
 */
public fun CombatUnit.availableAmmoBins(): List<Triple<MechLocation, Int, CriticalSlotContent.AmmoBin>> =
    criticalLayout.ammoBins().filter { (location, _, _) ->
        isLocationIntact(location, internalStructure)
    }

/**
 * Returns a copy of this unit with one shot consumed from the first non-empty,
 * non-destroyed-location bin of [ammoType]. Mirrors [CriticalLayout.consumeOneRound]
 * but skips bins in locations whose IS is 0.
 *
 * Returns this unit unchanged if no available bin exists. Callers should guard with
 * [battletech.tactical.attack.weapon.HasAmmoRule] to ensure a bin is available.
 */
public fun CombatUnit.consumeOneRoundFromAvailableBin(ammoType: AmmoType): CombatUnit {
    val (location, slotIndex, bin) = availableAmmoBins()
        .firstOrNull { (_, _, b) -> b.type == ammoType && b.shots > 0 }
        ?: return this
    return copy(
        criticalLayout = criticalLayout.withSlot(location, slotIndex, bin.copy(shots = bin.shots - 1)),
    )
}

public fun CriticalLayout.validate(weapons: List<Weapon>) {
    for ((location, expectedCount) in SLOT_COUNTS) {
        val locationSlots = byLocation[location]
            ?: throw IllegalStateException("missing location $location in critical layout")
        check(locationSlots.slots.size == expectedCount) {
            "expected $expectedCount slots in $location, found ${locationSlots.slots.size}"
        }
    }

    val headSlots = slotsAt(MechLocation.HEAD)
    check(headSlots == HEAD_FRAMEWORK) {
        "HEAD framework mismatch: expected $HEAD_FRAMEWORK, found $headSlots"
    }

    val centerTorsoSlots = slotsAt(MechLocation.CENTER_TORSO)
    check(centerTorsoSlots.take(10) == CENTER_TORSO_FRAMEWORK) {
        "CENTER_TORSO framework mismatch: expected $CENTER_TORSO_FRAMEWORK, found ${centerTorsoSlots.take(10)}"
    }

    for (arm in ARM_LOCATIONS) {
        val slots = slotsAt(arm)
        check(slots.getOrNull(0) == CriticalSlotContent.Actuator(ActuatorType.SHOULDER)) {
            "$arm slot 0 must be Actuator(SHOULDER), found ${slots.getOrNull(0)}"
        }
        check(slots.getOrNull(1) == CriticalSlotContent.Actuator(ActuatorType.UPPER_ARM)) {
            "$arm slot 1 must be Actuator(UPPER_ARM), found ${slots.getOrNull(1)}"
        }
    }

    for (leg in LEG_LOCATIONS) {
        val slots = slotsAt(leg)
        val expected = LEG_FRAMEWORK.map { CriticalSlotContent.Actuator(it) }
        check(slots.take(4) == expected) {
            "$leg framework mismatch: expected $expected, found ${slots.take(4)}"
        }
    }

    val weaponsById = weapons.associateBy { it.mountId }
    val allWeaponIds = byLocation.values.flatMap { it.slots }
        .filterIsInstance<CriticalSlotContent.WeaponMount>()
        .map { it.weaponId }
        .toSet()

    for (weaponId in allWeaponIds) {
        val weapon = weaponsById[weaponId]
            ?: throw IllegalStateException("no weapon found for mountId $weaponId")
        val positions = slotsForWeapon(weaponId)
        check(positions.size == weapon.criticalSlots) {
            "weapon ${weapon.name} ($weaponId) expects ${weapon.criticalSlots} slots, found ${positions.size}"
        }
        val locations = positions.map { it.first }.toSet()
        check(locations.size == 1) {
            "weapon ${weapon.name} ($weaponId) slots span multiple locations: $locations"
        }
        val indices = positions.map { it.second }.sorted()
        val isContiguous = indices.zipWithNext().all { (a, b) -> b == a + 1 }
        check(isContiguous) {
            "weapon ${weapon.name} ($weaponId) slots are not contiguous: $indices"
        }
    }

    for ((location, index, bin) in ammoBins()) {
        check(bin.shots > 0) {
            "ammo bin at $location slot $index has non-positive shots: ${bin.shots}"
        }
    }
}
