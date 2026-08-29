package battletech.tactical.model.mech

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

internal class MechModelCatalogTest {

    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `load every packaged classic variant`() {
        val catalog = MechModelCatalog.load()

        assertThat(catalog.variants).containsExactlyInAnyOrder(
            "LCT-1V",
            "STG-3R",
            "WSP-1A",
            "PXH-1",
            "GRF-1N",
            "SHD-2H",
            "WHM-6R",
            "MAD-3R",
            "ARC-2R",
            "AS7-D",
            "HBK-4G",
            "WVR-6R",
        )
    }

    @Test
    fun `load several variants from one external collection`() {
        val path = writeCollection(modelJson("TEST-1"), modelJson("TEST-2"))

        val catalog = MechModelCatalog.load(listOf(path))

        assertThat(catalog.variants).contains("TEST-1", "TEST-2")
        assertThat(catalog["TEST-1"].name).isEqualTo("Test TEST-1")
    }

    @Test
    fun `reject variant repeated within one collection`() {
        val path = writeCollection(modelJson("DUP"), modelJson("DUP"))

        val exception = assertThrows<MechLoadException> { MechModelCatalog.load(listOf(path)) }

        assertThat(exception.message).contains("'DUP' is repeated", path.toString())
    }

    @Test
    fun `reject variant repeated across external collections`() {
        val first = writeCollection(modelJson("DUP"), filename = "first.json")
        val second = writeCollection(modelJson("DUP"), filename = "second.json")

        val exception = assertThrows<MechLoadException> { MechModelCatalog.load(listOf(first, second)) }

        assertThat(exception.message).contains("first.json", "second.json", "DUP")
    }

    @Test
    fun `reject external variant that collides with packaged model`() {
        val path = writeCollection(modelJson("AS7-D"))

        val exception = assertThrows<MechLoadException> { MechModelCatalog.load(listOf(path)) }

        assertThat(exception.message).contains("AS7-D", "mech/classic.json", path.toString())
    }

    @Test
    fun `reject malformed missing and unknown fields`() {
        val malformed = tempDir.resolve("malformed.json").also { it.writeText("{not json") }
        val missing = tempDir.resolve("missing.json")
        val unknown = tempDir.resolve("unknown.json").also {
            it.writeText("""{"unexpected":true,"models":[]}""")
        }

        assertThat(assertThrows<MechLoadException> { MechModelCatalog.load(listOf(malformed)) }.message)
            .contains("Malformed mech file")
        assertThat(assertThrows<MechLoadException> { MechModelCatalog.load(listOf(missing)) }.message)
            .contains("Mech file not found")
        assertThrows<MechLoadException> { MechModelCatalog.load(listOf(unknown)) }
    }

    @Test
    fun `reject unknown weapon and loadout that cannot fit`() {
        val unknownWeapon = writeCollection(
            modelJson("UNKNOWN-WEAPON", loadout = """{"type":"weapon","location":"LEFT_ARM","weapon":"nope"}"""),
            filename = "unknown-weapon.json",
        )
        val overflowing = writeCollection(
            modelJson(
                "OVERFLOW",
                loadout = List(9) {
                    """{"type":"weapon","location":"LEFT_ARM","weapon":"mediumLaser"}"""
                }.joinToString(","),
            ),
            filename = "overflow.json",
        )

        assertThat(assertThrows<MechLoadException> { MechModelCatalog.load(listOf(unknownWeapon)) }.message)
            .contains("unknown weapon 'nope'")
        assertThat(assertThrows<MechLoadException> { MechModelCatalog.load(listOf(overflowing)) }.message)
            .contains("not enough contiguous free slots")
    }

    private fun writeCollection(vararg models: String, filename: String = "models.json"): Path {
        val path = tempDir.resolve(filename)
        path.writeText("""{"models":[${models.joinToString(",")}]}""")
        return path
    }

    private fun modelJson(variant: String, loadout: String = ""): String = """
        {
          "variant":"$variant",
          "name":"Test $variant",
          "tonnage":20,
          "walkingMP":4,
          "runningMP":6,
          "armor":{
            "head":0,
            "centerTorso":0,
            "centerTorsoRear":0,
            "leftTorso":0,
            "leftTorsoRear":0,
            "rightTorso":0,
            "rightTorsoRear":0,
            "leftArm":0,
            "rightArm":0,
            "leftLeg":0,
            "rightLeg":0
          },
          "loadout":[$loadout]
        }
    """.trimIndent()
}
