package battletech.tactical.unit

import battletech.tactical.model.MechLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

internal class MechModelsLayoutTest {

    private val expectedWeaponNames: Map<String, List<String>> = mapOf(
        "LCT-1V" to listOf("Medium Laser", "Machine Gun", "Machine Gun"),
        "STG-3R" to listOf("Medium Laser", "Machine Gun", "Machine Gun"),
        "WSP-1A" to listOf("Medium Laser", "SRM 2"),
        "PXH-1" to listOf("Large Laser", "Medium Laser", "Medium Laser", "Machine Gun", "Machine Gun"),
        "GRF-1N" to listOf("PPC", "LRM 10"),
        "SHD-2H" to listOf("AC/5", "LRM 5", "SRM 2", "Medium Laser"),
        "WHM-6R" to listOf(
            "PPC", "PPC",
            "Medium Laser", "Medium Laser",
            "Small Laser", "Small Laser",
            "Machine Gun", "Machine Gun",
            "SRM 6",
        ),
        "MAD-3R" to listOf("PPC", "PPC", "Medium Laser", "Medium Laser", "AC/5"),
        "ARC-2R" to listOf(
            "LRM 20", "LRM 20",
            "Medium Laser", "Medium Laser", "Medium Laser", "Medium Laser",
        ),
        "AS7-D" to listOf(
            "AC/20", "LRM 20", "SRM 6",
            "Medium Laser", "Medium Laser", "Medium Laser", "Medium Laser",
        ),
        "HBK-4G" to listOf("AC/20"),
        "WVR-6R" to listOf("SRM 6", "Medium Laser"),
    )

    @TestFactory
    fun `every canonical variant validates`(): List<DynamicTest> =
        expectedWeaponNames.keys.map { variant ->
            DynamicTest.dynamicTest(variant) {
                val model = MechModels[variant]
                model.criticalLayout.validate(model.weapons)
            }
        }

    @TestFactory
    fun `weaponIndex order matches the canonical placement order`(): List<DynamicTest> =
        expectedWeaponNames.map { (variant, expected) ->
            DynamicTest.dynamicTest(variant) {
                val model = MechModels[variant]
                assertThat(model.weapons.map { it.name }).isEqualTo(expected)
            }
        }

    @TestFactory
    fun `every placed weapon has a non-null location and mountId`(): List<DynamicTest> =
        expectedWeaponNames.keys.map { variant ->
            DynamicTest.dynamicTest(variant) {
                val model = MechModels[variant]
                model.weapons.forEach { weapon ->
                    assertThat(weapon.location).isNotNull()
                    assertThat(weapon.mountId).isNotNull()
                }
            }
        }

    @org.junit.jupiter.api.Test
    fun `HBK-4G AC20 occupies 10 WeaponMount slots in RIGHT_TORSO`() {
        val model = MechModels["HBK-4G"]
        val ac20 = model.weapons.single { it.name == "AC/20" }

        assertThat(ac20.location).isEqualTo(MechLocation.RIGHT_TORSO)
        val mountId = ac20.mountId!!
        assertThat(model.criticalLayout.weaponIdsAt(MechLocation.RIGHT_TORSO)).contains(mountId)

        val positions = model.criticalLayout.slotsForWeapon(mountId)
        assertThat(positions).hasSize(10)
        assertThat(positions.map { it.first }.toSet()).containsExactly(MechLocation.RIGHT_TORSO)
    }

    @org.junit.jupiter.api.Test
    fun `AS7-D places AC20 in RIGHT_TORSO and LRM20 in LEFT_TORSO with an AC20 ammo bin in RIGHT_TORSO`() {
        val model = MechModels["AS7-D"]
        val ac20 = model.weapons.single { it.name == "AC/20" }
        val lrm20 = model.weapons.single { it.name == "LRM 20" }

        assertThat(ac20.location).isEqualTo(MechLocation.RIGHT_TORSO)
        assertThat(lrm20.location).isEqualTo(MechLocation.LEFT_TORSO)

        val rightTorsoAmmoBins = model.criticalLayout.ammoBins()
            .filter { (location, _, _) -> location == MechLocation.RIGHT_TORSO }
            .map { (_, _, bin) -> bin }
        assertThat(rightTorsoAmmoBins).anySatisfy { bin ->
            assertThat(bin.type).isEqualTo(AmmoType.AC20)
        }
    }
}
