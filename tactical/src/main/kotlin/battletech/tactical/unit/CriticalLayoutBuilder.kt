package battletech.tactical.unit

import battletech.tactical.model.MechLocation

public data class MechCriticalBuild(public val layout: CriticalLayout, public val weapons: List<Weapon>)

public fun mechLayout(build: CriticalLayoutBuilder.() -> Unit): MechCriticalBuild {
    val builder = CriticalLayoutBuilder()
    build(builder)
    return builder.build()
}

private val HEAT_SINK_SCAN_ORDER: List<MechLocation> = listOf(
    MechLocation.LEFT_TORSO,
    MechLocation.RIGHT_TORSO,
    MechLocation.CENTER_TORSO,
    MechLocation.LEFT_ARM,
    MechLocation.RIGHT_ARM,
    MechLocation.LEFT_LEG,
    MechLocation.RIGHT_LEG,
)

private val JUMP_JET_SCAN_ORDER: List<MechLocation> = listOf(
    MechLocation.CENTER_TORSO,
    MechLocation.LEFT_TORSO,
    MechLocation.RIGHT_TORSO,
    MechLocation.LEFT_LEG,
    MechLocation.RIGHT_LEG,
)

public class CriticalLayoutBuilder {
    private val slotsByLocation: MutableMap<MechLocation, MutableList<CriticalSlotContent>> = mutableMapOf(
        MechLocation.HEAD to mutableListOf(
            CriticalSlotContent.LifeSupport,
            CriticalSlotContent.Sensors,
            CriticalSlotContent.Cockpit,
            CriticalSlotContent.Empty,
            CriticalSlotContent.Sensors,
            CriticalSlotContent.LifeSupport,
        ),
        MechLocation.CENTER_TORSO to mutableListOf(
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
            CriticalSlotContent.Empty,
            CriticalSlotContent.Empty,
        ),
        MechLocation.LEFT_TORSO to MutableList(SLOT_COUNTS[MechLocation.LEFT_TORSO]!!) { CriticalSlotContent.Empty },
        MechLocation.RIGHT_TORSO to MutableList(SLOT_COUNTS[MechLocation.RIGHT_TORSO]!!) { CriticalSlotContent.Empty },
        MechLocation.LEFT_ARM to mutableListOf(
            CriticalSlotContent.Actuator(ActuatorType.SHOULDER),
            CriticalSlotContent.Actuator(ActuatorType.UPPER_ARM),
            CriticalSlotContent.Actuator(ActuatorType.LOWER_ARM),
            CriticalSlotContent.Actuator(ActuatorType.HAND),
            *Array(8) { CriticalSlotContent.Empty },
        ),
        MechLocation.RIGHT_ARM to mutableListOf(
            CriticalSlotContent.Actuator(ActuatorType.SHOULDER),
            CriticalSlotContent.Actuator(ActuatorType.UPPER_ARM),
            CriticalSlotContent.Actuator(ActuatorType.LOWER_ARM),
            CriticalSlotContent.Actuator(ActuatorType.HAND),
            *Array(8) { CriticalSlotContent.Empty },
        ),
        MechLocation.LEFT_LEG to mutableListOf(
            CriticalSlotContent.Actuator(ActuatorType.HIP),
            CriticalSlotContent.Actuator(ActuatorType.UPPER_LEG),
            CriticalSlotContent.Actuator(ActuatorType.LOWER_LEG),
            CriticalSlotContent.Actuator(ActuatorType.FOOT),
            CriticalSlotContent.Empty,
            CriticalSlotContent.Empty,
        ),
        MechLocation.RIGHT_LEG to mutableListOf(
            CriticalSlotContent.Actuator(ActuatorType.HIP),
            CriticalSlotContent.Actuator(ActuatorType.UPPER_LEG),
            CriticalSlotContent.Actuator(ActuatorType.LOWER_LEG),
            CriticalSlotContent.Actuator(ActuatorType.FOOT),
            CriticalSlotContent.Empty,
            CriticalSlotContent.Empty,
        ),
    )

    private val placedWeapons: MutableList<Weapon> = mutableListOf()
    private var nextMountId: Int = 0

    public fun omitActuators(location: MechLocation, lowerArm: Boolean = false, hand: Boolean = false) {
        val slots = slotsByLocation.getValue(location)
        if (lowerArm) {
            val index = slots.indexOf(CriticalSlotContent.Actuator(ActuatorType.LOWER_ARM))
            if (index >= 0) slots[index] = CriticalSlotContent.Empty
        }
        if (hand) {
            val index = slots.indexOf(CriticalSlotContent.Actuator(ActuatorType.HAND))
            if (index >= 0) slots[index] = CriticalSlotContent.Empty
        }
    }

    public fun place(location: MechLocation, model: WeaponModel): WeaponMountId {
        val id = WeaponMountId(nextMountId++)
        val weapon = Weapon(model = model, mountId = id, location = location)
        val slots = slotsByLocation.getValue(location)
        val startIndex = checkNotNull(findContiguousEmptyRun(slots, weapon.criticalSlots)) { "not enough contiguous free slots in $location for ${weapon.name}" }
        for (offset in 0 until weapon.criticalSlots) {
            slots[startIndex + offset] = CriticalSlotContent.WeaponMount(id)
        }
        placedWeapons.add(weapon)
        return id
    }

    public fun ammo(location: MechLocation, type: AmmoType, tons: Int = 1) {
        val slots = slotsByLocation.getValue(location)
        val emptyIndices = slots.indices.filter { slots[it] == CriticalSlotContent.Empty }
        check(emptyIndices.size >= tons) {
            "not enough free slots in $location for $tons ton(s) of ${type.name} ammo"
        }
        for (i in 0 until tons) {
            slots[emptyIndices[i]] = CriticalSlotContent.AmmoBin(type, type.shotsPerTon)
        }
    }

    public fun heatSinks(count: Int) {
        placeInScanOrder(count, HEAT_SINK_SCAN_ORDER, CriticalSlotContent.HeatSink) {
            "not enough free slots to place $count heat sink(s)"
        }
    }

    public fun jumpJets(count: Int) {
        placeInScanOrder(count, JUMP_JET_SCAN_ORDER, CriticalSlotContent.JumpJet) {
            "not enough free slots to place $count jump jet(s)"
        }
    }

    private fun placeInScanOrder(
        count: Int,
        order: List<MechLocation>,
        content: CriticalSlotContent,
        errorMessage: () -> String,
    ) {
        var remaining = count
        for (location in order) {
            if (remaining == 0) break
            val slots = slotsByLocation.getValue(location)
            for (i in slots.indices) {
                if (remaining == 0) break
                if (slots[i] == CriticalSlotContent.Empty) {
                    slots[i] = content
                    remaining--
                }
            }
        }
        check(remaining == 0) { errorMessage() }
    }

    private fun findContiguousEmptyRun(slots: List<CriticalSlotContent>, length: Int): Int? {
        var runStart = -1
        var runLength = 0
        for (i in slots.indices) {
            if (slots[i] == CriticalSlotContent.Empty) {
                if (runLength == 0) runStart = i
                runLength++
                if (runLength == length) return runStart
            } else {
                runLength = 0
            }
        }
        return null
    }

    public fun build(): MechCriticalBuild {
        val byLocation = slotsByLocation.mapValues { (location, slots) ->
            LocationSlots(location, slots.toList())
        }
        return MechCriticalBuild(CriticalLayout(byLocation), placedWeapons.toList())
    }
}
