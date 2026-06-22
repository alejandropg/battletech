package battletech.tactical.unit

import battletech.tactical.model.MechLocation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class CriticalLayoutTest {

    private fun buildSampleMech(): MechCriticalBuild = mechLayout {
        place(MechLocation.RIGHT_TORSO) { Weapons.ac20() }
        ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, tons = 1)
        place(MechLocation.LEFT_ARM) { Weapons.mediumLaser() }
        place(MechLocation.RIGHT_ARM) { Weapons.mediumLaser() }
        heatSinks(2)
        jumpJets(2)
    }

    @Test
    fun `a small built layout passes validate`() {
        val (layout, weapons) = buildSampleMech()
        layout.validate(weapons)
    }

    @Test
    fun `AC20 occupies exactly 10 contiguous WeaponMount slots in RIGHT_TORSO`() {
        val (layout, weapons) = buildSampleMech()
        val ac20 = weapons.first { it.name == "AC/20" }

        assertThat(ac20.location).isEqualTo(MechLocation.RIGHT_TORSO)
        assertThat(ac20.mountId).isNotNull()

        val positions = layout.slotsForWeapon(ac20.mountId!!)
        assertThat(positions).hasSize(10)
        assertThat(positions.map { it.first }.toSet()).containsExactly(MechLocation.RIGHT_TORSO)

        val indices = positions.map { it.second }.sorted()
        assertThat(indices.zipWithNext().all { (a, b) -> b == a + 1 }).isTrue()
    }

    @Test
    fun `validate throws when a weapon's span is corrupted`() {
        val srm6 = Weapons.srm6().copy(mountId = WeaponMountId(0), location = MechLocation.LEFT_TORSO)
        val slots = MutableList<CriticalSlotContent>(SLOT_COUNTS[MechLocation.LEFT_TORSO]!!) { CriticalSlotContent.Empty }
        slots[0] = CriticalSlotContent.WeaponMount(WeaponMountId(0))

        val byLocation = mapOf(
            MechLocation.HEAD to LocationSlots(
                MechLocation.HEAD,
                listOf(
                    CriticalSlotContent.LifeSupport,
                    CriticalSlotContent.Sensors,
                    CriticalSlotContent.Cockpit,
                    CriticalSlotContent.Empty,
                    CriticalSlotContent.Sensors,
                    CriticalSlotContent.LifeSupport,
                ),
            ),
            MechLocation.CENTER_TORSO to LocationSlots(
                MechLocation.CENTER_TORSO,
                listOf(
                    CriticalSlotContent.Engine, CriticalSlotContent.Engine, CriticalSlotContent.Engine,
                    CriticalSlotContent.Gyro, CriticalSlotContent.Gyro, CriticalSlotContent.Gyro, CriticalSlotContent.Gyro,
                    CriticalSlotContent.Engine, CriticalSlotContent.Engine, CriticalSlotContent.Engine,
                    CriticalSlotContent.Empty, CriticalSlotContent.Empty,
                ),
            ),
            MechLocation.LEFT_TORSO to LocationSlots(MechLocation.LEFT_TORSO, slots),
            MechLocation.RIGHT_TORSO to LocationSlots(
                MechLocation.RIGHT_TORSO,
                MutableList(SLOT_COUNTS[MechLocation.RIGHT_TORSO]!!) { CriticalSlotContent.Empty },
            ),
            MechLocation.LEFT_ARM to LocationSlots(
                MechLocation.LEFT_ARM,
                listOf(
                    CriticalSlotContent.Actuator(ActuatorType.SHOULDER),
                    CriticalSlotContent.Actuator(ActuatorType.UPPER_ARM),
                ) + List(10) { CriticalSlotContent.Empty },
            ),
            MechLocation.RIGHT_ARM to LocationSlots(
                MechLocation.RIGHT_ARM,
                listOf(
                    CriticalSlotContent.Actuator(ActuatorType.SHOULDER),
                    CriticalSlotContent.Actuator(ActuatorType.UPPER_ARM),
                ) + List(10) { CriticalSlotContent.Empty },
            ),
            MechLocation.LEFT_LEG to LocationSlots(
                MechLocation.LEFT_LEG,
                listOf(
                    CriticalSlotContent.Actuator(ActuatorType.HIP),
                    CriticalSlotContent.Actuator(ActuatorType.UPPER_LEG),
                    CriticalSlotContent.Actuator(ActuatorType.LOWER_LEG),
                    CriticalSlotContent.Actuator(ActuatorType.FOOT),
                    CriticalSlotContent.Empty,
                    CriticalSlotContent.Empty,
                ),
            ),
            MechLocation.RIGHT_LEG to LocationSlots(
                MechLocation.RIGHT_LEG,
                listOf(
                    CriticalSlotContent.Actuator(ActuatorType.HIP),
                    CriticalSlotContent.Actuator(ActuatorType.UPPER_LEG),
                    CriticalSlotContent.Actuator(ActuatorType.LOWER_LEG),
                    CriticalSlotContent.Actuator(ActuatorType.FOOT),
                    CriticalSlotContent.Empty,
                    CriticalSlotContent.Empty,
                ),
            ),
        )

        val layout = CriticalLayout(byLocation)

        assertThatThrownBy { layout.validate(listOf(srm6)) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `HEAD has Cockpit at slot 2`() {
        val (layout, _) = buildSampleMech()
        assertThat(layout.slotsAt(MechLocation.HEAD)[2]).isEqualTo(CriticalSlotContent.Cockpit)
    }

    @Test
    fun `CENTER_TORSO has Gyro at slots 3 through 6`() {
        val (layout, _) = buildSampleMech()
        val centerTorso = layout.slotsAt(MechLocation.CENTER_TORSO)
        assertThat(centerTorso.subList(3, 7)).containsOnly(CriticalSlotContent.Gyro)
    }

    @Test
    fun `omitActuators removes the hand actuator from an arm`() {
        val build = mechLayout {
            omitActuators(MechLocation.LEFT_ARM, hand = true)
            place(MechLocation.LEFT_ARM) { Weapons.mediumLaser() }
        }

        val slots = build.layout.slotsAt(MechLocation.LEFT_ARM)
        assertThat(slots).doesNotContain(CriticalSlotContent.Actuator(ActuatorType.HAND))
    }
}
