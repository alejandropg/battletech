package battletech.tactical.model.game

import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.map.GameMapCatalog
import battletech.tactical.model.mech.MechModelCatalog
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

internal class GameStateLoaderTest {

    @field:TempDir
    private lateinit var tempDir: Path

    private val catalog = GameMapCatalog.load()
    private val loader = GameStateLoader(catalog)

    @Test
    fun `packaged default game reproduces the existing starting state`() {
        val state = loader.resolve(DEFAULT_GAME_NAME)

        assertThat(state.map.name).isEqualTo("battletech-classic")
        assertThat(state.units.all.map { it.id.value })
            .containsExactly("A1", "H1", "W1", "W2")
        assertUnit(state, "A1", PlayerId.PLAYER_1, "AS7-D", HexCoordinates(1, 1), HexDirection.SE, 4, 5)
        assertUnit(state, "H1", PlayerId.PLAYER_1, "HBK-4G", HexCoordinates(2, 3), HexDirection.N, 4, 5)
        assertUnit(state, "W1", PlayerId.PLAYER_2, "WVR-6R", HexCoordinates(7, 3), HexDirection.N, 4, 4)
        assertUnit(state, "W2", PlayerId.PLAYER_2, "WVR-6R", HexCoordinates(8, 5), HexDirection.N, 4, 5)
    }

    @Test
    fun `load external game that selects a registered external map`() {
        val map = tempDir.resolve("arena.json")
        map.writeText("""{"width":2,"height":2,"hexes":[]}""")
        val game = writeGame(gameJson(map = "arena"))
        val externalLoader = GameStateLoader(GameMapCatalog.load(listOf(map)))

        val state = externalLoader.load(game)

        assertThat(state.map.name).isEqualTo("arena")
        assertThat(state.units.all).hasSize(2)
        assertThat(state.units.byId(UnitId("P1")).position).isEqualTo(HexCoordinates(0, 0))
    }

    @Test
    fun `load game that selects a variant from an external mech collection`() {
        val mech = tempDir.resolve("custom-mechs.json")
        mech.writeText(
            """
            {
              "models":[{
                "variant":"CUSTOM-1",
                "name":"Custom CUSTOM-1",
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
            """.trimIndent(),
        )
        val game = writeGame(gameJson(firstUnit = unitJson(variant = "CUSTOM-1")))
        val externalLoader = GameStateLoader(catalog, MechModelCatalog.load(listOf(mech)))

        val state = externalLoader.load(game)

        assertThat(state.units.byId(UnitId("P1")).variant).isEqualTo("CUSTOM-1")
    }

    @Test
    fun `built-in game index lists every shipped game and each one loads`() {
        val names = loader.builtInNames()

        assertThat(names).containsExactly("default")
        names.forEach { name -> assertThat(loader.resolve(name).units).describedAs(name).isNotEmpty() }
    }

    @Test
    fun `reject unknown fields`() {
        val game = writeGame(gameJson().replaceFirst("\"map\":", "\"unexpected\":true,\"map\":"))

        assertThrows<GameLoadException> { loader.load(game) }
    }

    @Test
    fun `reject missing required unit field`() {
        val content = gameJson().replaceFirst("\"gunnerySkill\":4,", "")
        val game = writeGame(content)

        assertThrows<GameLoadException> { loader.load(game) }
    }

    @Test
    fun `reject blank map name`() {
        val game = writeGame(gameJson(map = ""))

        val exception = assertThrows<GameLoadException> { loader.load(game) }

        assertThat(exception.message).contains("Game map name must not be blank")
    }

    @Test
    fun `reject malformed facing`() {
        val game = writeGame(gameJson(firstUnit = unitJson(facing = "E")))

        assertThrows<GameLoadException> { loader.load(game) }
    }

    @Test
    fun `reject unknown map`() {
        val game = writeGame(gameJson(map = "missing"))

        val exception = assertThrows<GameLoadException> { loader.load(game) }

        assertThat(exception.message).contains("Could not resolve game map 'missing'")
    }

    @Test
    fun `reject unknown mech variant`() {
        val game = writeGame(gameJson(firstUnit = unitJson(variant = "UNKNOWN")))

        val exception = assertThrows<GameLoadException> { loader.load(game) }

        assertThat(exception.message).contains("unknown mech variant 'UNKNOWN'")
    }

    @Test
    fun `reject player outside one and two`() {
        val game = writeGame(gameJson(firstUnit = unitJson(player = 3)))

        val exception = assertThrows<GameLoadException> { loader.load(game) }

        assertThat(exception.message).contains("invalid player 3")
    }

    @Test
    fun `reject roster without a unit for each player`() {
        val game = writeGame(gameJson(secondUnit = unitJson(id = "P2", player = 1, col = 2, row = 1)))

        val exception = assertThrows<GameLoadException> { loader.load(game) }

        assertThat(exception.message).contains("missing player 2")
    }

    @Test
    fun `reject blank and duplicate unit ids`() {
        val blank = writeGame(gameJson(firstUnit = unitJson(id = "")), "blank.json")
        val duplicate = writeGame(gameJson(secondUnit = unitJson(id = "P1", player = 2, col = 2, row = 1)), "duplicate.json")

        assertThat(assertThrows<GameLoadException> { loader.load(blank) }.message).contains("must not be blank")
        assertThat(assertThrows<GameLoadException> { loader.load(duplicate) }.message).contains("Duplicate unit id: P1")
    }

    @Test
    fun `reject skill outside zero through eight`() {
        val low = writeGame(gameJson(firstUnit = unitJson(gunnerySkill = -1)), "low.json")
        val high = writeGame(gameJson(firstUnit = unitJson(pilotingSkill = 9)), "high.json")

        assertThat(assertThrows<GameLoadException> { loader.load(low) }.message).contains("gunnerySkill must be in 0..8")
        assertThat(assertThrows<GameLoadException> { loader.load(high) }.message).contains("pilotingSkill must be in 0..8")
    }

    @Test
    fun `accept skill range endpoints`() {
        val game = writeGame(gameJson(firstUnit = unitJson(gunnerySkill = 0, pilotingSkill = 8)))

        val state = loader.load(game)

        val unit = state.units.byId(UnitId("P1"))
        assertThat(unit.gunnerySkill).isZero()
        assertThat(unit.pilotingSkill).isEqualTo(8)
    }

    @Test
    fun `reject position outside map and overlapping positions`() {
        val outside = writeGame(gameJson(firstUnit = unitJson(col = 11, row = 1)), "outside.json")
        val overlap = writeGame(gameJson(secondUnit = unitJson(id = "P2", player = 2)), "overlap.json")

        assertThat(assertThrows<GameLoadException> { loader.load(outside) }.message).contains("outside map 'default'")
        assertThat(assertThrows<GameLoadException> { loader.load(overlap) }.message).contains("More than one unit occupies position")
    }

    @Test
    fun `missing packaged game names available games`() {
        val exception = assertThrows<GameLoadException> { loader.resolve("missing") }

        assertThat(exception.message).isEqualTo("Game resource not found: game/missing.json\nBuilt-in games: default")
    }

    private fun writeGame(content: String, filename: String = "game.json"): Path {
        val path = tempDir.resolve(filename)
        path.writeText(content)
        return path
    }

    private fun gameJson(
        map: String = "default",
        firstUnit: String = unitJson(),
        secondUnit: String = unitJson(id = "P2", player = 2, col = 2, row = 1),
    ): String = """
        {
          "map":"$map",
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

    private fun assertUnit(
        state: GameState,
        id: String,
        owner: PlayerId,
        variant: String,
        position: HexCoordinates,
        facing: HexDirection,
        gunnerySkill: Int,
        pilotingSkill: Int,
    ) {
        val unit = state.units.byId(UnitId(id))
        assertThat(unit.owner).isEqualTo(owner)
        assertThat(unit.variant).isEqualTo(variant)
        assertThat(unit.position).isEqualTo(position)
        assertThat(unit.facing).isEqualTo(facing)
        assertThat(unit.gunnerySkill).isEqualTo(gunnerySkill)
        assertThat(unit.pilotingSkill).isEqualTo(pilotingSkill)
    }
}
