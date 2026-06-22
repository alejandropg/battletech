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
