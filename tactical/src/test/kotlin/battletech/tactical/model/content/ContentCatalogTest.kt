package battletech.tactical.model.content

import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.map.MapLoadException
import battletech.tactical.model.mech.MechLoadException
import battletech.tactical.model.unit.UnitLoadException
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Covers assembling a [GameState] from a [ContentCatalog]: the map/unit-collection pairing that
 * replaces the old single game file, plus the guarantee that launching and listing can never
 * disagree because both go through this same catalog (see [ContentCatalog]'s class KDoc).
 */
internal class ContentCatalogTest {

    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `packaged defaults reproduce the existing starting state`() {
        val state = ContentCatalog.load().resolveGame()

        assertThat(state.map.name).isEqualTo("battletech-classic")
        assertThat(state.units.all.map { it.id.value }).containsExactly("A1", "H1", "W1", "W2")
        val a1 = state.units.byId(UnitId("A1"))
        assertThat(a1.owner).isEqualTo(PlayerId.PLAYER_1)
        assertThat(a1.variant).isEqualTo("AS7-D")
        assertThat(a1.position).isEqualTo(HexCoordinates(1, 1))
        assertThat(a1.facing).isEqualTo(HexDirection.SE)
    }

    @Test
    fun `a roster is playable on a different registered map than it was authored against`() {
        val state = ContentCatalog.load().resolveGame(mapName = "river-valley")

        assertThat(state.map.name).isEqualTo("river-valley")
        assertThat(state.units.all).hasSize(4)
    }

    @Test
    fun `external map paired with external unit collection`() {
        val map = tempDir.resolve("arena.json")
        map.writeText("""{"width":2,"height":2,"hexes":[]}""")
        val units = tempDir.resolve("duel.json")
        units.writeText(unitsJson())

        val catalog = ContentCatalog.load(mapPaths = listOf(map), unitPaths = listOf(units))
        val state = catalog.resolveGame(mapName = "arena", unitsName = "duel")

        assertThat(state.map.name).isEqualTo("arena")
        assertThat(state.units.byId(UnitId("P1")).position).isEqualTo(HexCoordinates(0, 0))
    }

    @Test
    fun `reject unknown map name`() {
        val exception = assertThrows<MapLoadException> { ContentCatalog.load().resolveGame(mapName = "missing") }

        assertThat(exception.message).contains("Map not found in catalog: missing")
    }

    @Test
    fun `reject unknown unit collection name`() {
        val exception = assertThrows<UnitLoadException> { ContentCatalog.load().resolveGame(unitsName = "missing") }

        assertThat(exception.message).contains("Unit collection not found in catalog: missing")
    }

    @Test
    fun `reject a roster whose position falls outside the chosen map, naming both`() {
        val units = tempDir.resolve("tiny.json")
        units.writeText(unitsJson(firstUnit = unitJson(col = 99, row = 99)))
        val catalog = ContentCatalog.load(unitPaths = listOf(units))

        val exception = assertThrows<UnitLoadException> {
            catalog.resolveGame(mapName = "battletech-classic", unitsName = "tiny")
        }

        assertThat(exception.message).contains("outside map 'battletech-classic'")
    }

    @Test
    fun `reject overlapping positions and a roster missing a player`() {
        val overlap = tempDir.resolve("overlap.json")
        overlap.writeText(unitsJson(secondUnit = unitJson(id = "P2", player = 2)))
        val missingPlayer = tempDir.resolve("missing-player.json")
        missingPlayer.writeText(unitsJson(secondUnit = unitJson(id = "P2", player = 1, col = 2, row = 1)))
        val catalog = ContentCatalog.load(unitPaths = listOf(overlap, missingPlayer))

        assertThat(assertThrows<UnitLoadException> { catalog.resolveGame(unitsName = "overlap") }.message)
            .contains("More than one unit occupies position")
        assertThat(assertThrows<UnitLoadException> { catalog.resolveGame(unitsName = "missing-player") }.message)
            .contains("missing player 2")
    }

    @Test
    fun `reject unknown mech variant`() {
        val units = tempDir.resolve("bad-variant.json")
        units.writeText(unitsJson(firstUnit = unitJson(variant = "NOPE")))
        val catalog = ContentCatalog.load(unitPaths = listOf(units))

        val exception = assertThrows<UnitLoadException> { catalog.resolveGame(unitsName = "bad-variant") }

        assertThat(exception.message).contains("unknown mech variant 'NOPE'")
    }

    @Test
    fun `listing and launching agree when two registered mech files repeat a variant`() {
        val first = tempDir.resolve("first.json")
        first.writeText(mechJson("DUP"))
        val second = tempDir.resolve("second.json")
        second.writeText(mechJson("DUP"))

        // A catalog that disagreed with itself would let --list-mechs succeed while launch (or
        // vice versa) failed. Loading itself is the single point where this is decided.
        assertThrows<MechLoadException> { ContentCatalog.load(mechPaths = listOf(first, second)) }
    }

    @Test
    fun `listing exposes every registered map, mech collection, and unit collection`() {
        val listing = ContentCatalog.load().listing()

        assertThat(listing.maps.map { it.name }).contains("battletech-classic")
        assertThat(listing.mechs.first { it.name == "classic" }.items).contains("AS7-D")
        assertThat(listing.units.first { it.name == "default" }.items).containsExactly("A1", "H1", "W1", "W2")
    }

    @Test
    fun `contribution exposes every registered map and mech`() {
        val map = tempDir.resolve("arena.json")
        map.writeText("""{"width":10,"height":10,"hexes":[]}""")
        val catalog = ContentCatalog.load(mapPaths = listOf(map))

        val contribution = catalog.contribution()

        assertThat(contribution.maps.map { it.name }).contains("arena", "battletech-classic")
        assertThat(contribution.mechs.map { it.variant }).contains("AS7-D")
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
        col: Int = 1,
        row: Int = 1,
    ): String = """
        {
          "id":"$id",
          "player":$player,
          "variant":"$variant",
          "gunnerySkill":4,
          "pilotingSkill":5,
          "position":{"col":$col,"row":$row},
          "facing":"N"
        }
    """.trimIndent()

    private fun mechJson(variant: String): String = """
        {
          "models":[{
            "variant":"$variant",
            "name":"Test $variant",
            "tonnage":20,
            "walkingMP":4,
            "runningMP":6,
            "armor":{
              "head":0,"centerTorso":0,"centerTorsoRear":0,
              "leftTorso":0,"leftTorsoRear":0,"rightTorso":0,"rightTorsoRear":0,
              "leftArm":0,"rightArm":0,"leftLeg":0,"rightLeg":0
            }
          }]
        }
    """.trimIndent()
}
