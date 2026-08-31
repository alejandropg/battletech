package battletech.tactical.model.unit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Covers [UnitFileLoader]/[UnitFile] at the decode level: every check intrinsic to a unit spec,
 * independent of any map or mech catalog. Map- and mech-dependent assembly ([UnitFile.toRoster])
 * is covered by `ContentCatalogTest` instead, since it needs a [battletech.tactical.model.map.GameMapCatalog]
 * and [battletech.tactical.model.mech.MechModelCatalog] to exercise.
 */
internal class UnitFileLoaderTest {

    @field:TempDir
    private lateinit var tempDir: Path

    private val loader = UnitFileLoader()

    @Test
    fun `packaged default collection lists the existing sample roster`() {
        val file = loader.resolve(DEFAULT_UNITS_NAME)

        assertThat(file.unitIds()).containsExactly("A1", "H1", "W1", "W2")
    }

    @Test
    fun `built-in unit index lists every shipped collection and each one resolves`() {
        val names = loader.builtInNames()

        assertThat(names).containsExactly("default")
        names.forEach { name -> assertThat(loader.resolve(name).unitIds()).describedAs(name).isNotEmpty() }
    }

    @Test
    fun `reject unknown fields`() {
        val file = writeUnits(unitsJson().replaceFirst("\"units\":", "\"unexpected\":true,\"units\":"))

        assertThrows<UnitLoadException> { loader.load(file) }
    }

    @Test
    fun `reject missing required unit field`() {
        val content = unitsJson().replaceFirst("\"gunnerySkill\":4,", "")

        assertThrows<UnitLoadException> { loader.load(writeUnits(content)) }
    }

    @Test
    fun `reject malformed facing`() {
        val file = writeUnits(unitsJson(firstUnit = unitJson(facing = "E")))

        assertThrows<UnitLoadException> { loader.load(file) }
    }

    @Test
    fun `reject player outside one and two`() {
        val file = writeUnits(unitsJson(firstUnit = unitJson(player = 3)))

        val exception = assertThrows<UnitLoadException> { loader.load(file) }

        assertThat(exception.message).contains("invalid player 3")
    }

    @Test
    fun `reject blank and duplicate unit ids`() {
        val blank = writeUnits(unitsJson(firstUnit = unitJson(id = "")), "blank.json")
        val duplicate = writeUnits(unitsJson(secondUnit = unitJson(id = "P1", player = 2, col = 2, row = 1)), "duplicate.json")

        assertThat(assertThrows<UnitLoadException> { loader.load(blank) }.message).contains("must not be blank")
        assertThat(assertThrows<UnitLoadException> { loader.load(duplicate) }.message).contains("Duplicate unit id: P1")
    }

    @Test
    fun `reject skill outside zero through eight`() {
        val low = writeUnits(unitsJson(firstUnit = unitJson(gunnerySkill = -1)), "low.json")
        val high = writeUnits(unitsJson(firstUnit = unitJson(pilotingSkill = 9)), "high.json")

        assertThat(assertThrows<UnitLoadException> { loader.load(low) }.message).contains("gunnerySkill must be in 0..8")
        assertThat(assertThrows<UnitLoadException> { loader.load(high) }.message).contains("pilotingSkill must be in 0..8")
    }

    @Test
    fun `accept skill range endpoints`() {
        val file = writeUnits(unitsJson(firstUnit = unitJson(gunnerySkill = 0, pilotingSkill = 8)))

        assertThat(loader.load(file).unitIds()).contains("P1")
    }

    @Test
    fun `missing packaged collection names available collections`() {
        val exception = assertThrows<UnitLoadException> { loader.resolve("missing") }

        assertThat(exception.message).isEqualTo("Unit resource not found: unit/missing.json\nBuilt-in units: default")
    }

    private fun writeUnits(content: String, filename: String = "units.json"): Path {
        val path = tempDir.resolve(filename)
        path.writeText(content)
        return path
    }

    private fun unitsJson(
        firstUnit: String = unitJson(),
        secondUnit: String = unitJson(id = "P2", player = 2, col = 2, row = 1),
    ): String = """
        {
          "units":[
            $firstUnit,
            $secondUnit
          ]
        }
    """.trimIndent()

    private fun unitJson(
        id: String = "P1",
        player: Int = 1,
        variant: String = "LCT-1V",
        gunnerySkill: Int = 4,
        pilotingSkill: Int = 5,
        col: Int = 1,
        row: Int = 1,
        facing: String = "N",
    ): String = """
        {
          "id":"$id",
          "player":$player,
          "variant":"$variant",
          "gunnerySkill":$gunnerySkill,
          "pilotingSkill":$pilotingSkill,
          "position":{"col":$col,"row":$row},
          "facing":"$facing"
        }
    """.trimIndent()
}
