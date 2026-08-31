package battletech.tui

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

internal class CliArgsTest {

    /** parseArgs is process-exiting in production; this is the exit lambda's escape hatch for
     * tests, carrying the exit code and everything Clikt printed via the recorder-backed terminal. */
    private class ExitCalled(val statusCode: Int, val output: String) : Exception()

    private fun parse(vararg args: String): Launch {
        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE)
        return parseArgs(arrayOf(*args), Terminal(terminalInterface = recorder)) {
            throw ExitCalled(it, recorder.output())
        }
    }

    private fun failing(vararg args: String): ExitCalled {
        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE)
        return assertThrows(ExitCalled::class.java) {
            parseArgs(arrayOf(*args), Terminal(terminalInterface = recorder)) {
                throw ExitCalled(it, recorder.output())
            }
        }
    }

    @Nested
    inner class NoDefaultCommand {
        @Test
        fun `no args is a usage error, not an implicit hot-seat`() {
            assertEquals(1, failing().statusCode)
        }

        @Test
        fun `no args prints the root help on stderr`() {
            assertTrue(failing().output.contains("hot-seat"))
        }
    }

    @Nested
    inner class HotSeat {
        @Test
        fun `bare hot-seat resolves with every default`() {
            assertEquals(Mode.HotSeat(), parse("hot-seat").mode)
        }

        @Test
        fun `hot-seat --map name selects the board`() {
            assertEquals(Mode.HotSeat(Setup(mapName = "river-valley")), parse("hot-seat", "--map", "river-valley").mode)
        }

        @Test
        fun `hot-seat --unit name selects the roster`() {
            assertEquals(Mode.HotSeat(Setup(unitsName = "duel")), parse("hot-seat", "--unit", "duel").mode)
        }

        @Test
        fun `hot-seat --map with no value throws`() {
            failing("hot-seat", "--map")
        }
    }

    @Nested
    inner class HostCommand {
        @Test
        fun `host with no port uses the default port`() {
            assertEquals(Mode.Host(), parse("host").mode)
        }

        @Test
        fun `host --port N uses the given port`() {
            assertEquals(Mode.Host(port = 5555), parse("host", "--port", "5555").mode)
        }

        @Test
        fun `host --port with a non-integer value throws`() {
            failing("host", "--port", "nope")
        }

        @Test
        fun `host --port with no value throws`() {
            failing("host", "--port")
        }

        @Test
        fun `host with an unknown trailing flag throws`() {
            failing("host", "--bogus")
        }

        @Test
        fun `host --map and --unit resolve to Host`() {
            assertEquals(
                Mode.Host(setup = Setup(mapName = "river-valley", unitsName = "duel")),
                parse("host", "--map", "river-valley", "--unit", "duel").mode,
            )
        }
    }

    @Nested
    inner class JoinCommand {
        @Test
        fun `join ip --session id resolves with the default port`() {
            val mode = parse("join", "192.168.1.5", "--session", "ABC123").mode
            assertEquals(Mode.Join(host = "192.168.1.5", sessionId = "ABC123"), mode)
        }

        @Test
        fun `join ip colon port --session id splits host and port`() {
            val mode = parse("join", "192.168.1.5:9999", "--session", "ABC123").mode
            assertEquals(Mode.Join(host = "192.168.1.5", port = 9999, sessionId = "ABC123"), mode)
        }

        @Test
        fun `join with no host throws`() {
            failing("join")
        }

        @Test
        fun `join with no --session throws`() {
            failing("join", "192.168.1.5")
        }

        @Test
        fun `join --session with no id value throws`() {
            failing("join", "192.168.1.5", "--session")
        }

        @Test
        fun `join with malformed host colon port throws`() {
            failing("join", "192.168.1.5:notaport", "--session", "ABC123")
        }

        @Test
        fun `join with empty host before the colon throws`() {
            failing("join", ":9999", "--session", "ABC123")
        }

        @Test
        fun `an ADDRESS with two colons is rejected`() {
            val ex = failing("join", "1.2.3.4:99:88", "--session", "ABC123")
            assertTrue(ex.output.contains("malformed port"))
        }

        @Test
        fun `join has no --map -- the map comes from the host`() {
            val ex = failing("join", "192.168.1.5", "--session", "s", "--map", "x")
            assertTrue(ex.output.contains("--map"))
        }

        @Test
        fun `join has no --unit -- the roster comes from the host`() {
            val ex = failing("join", "192.168.1.5", "--session", "s", "--unit", "x")
            assertTrue(ex.output.contains("--unit"))
        }

        @Test
        fun `join accepts --add-map, --add-mech, and --add-unit for local drift checks`() {
            val mode = parse(
                "--add-map", "x.json", "--add-mech", "y.json", "--add-unit", "z.json",
                "join", "192.168.1.5", "--session", "ABC123",
            ).mode
            assertEquals(Mode.Join(host = "192.168.1.5", sessionId = "ABC123"), mode)
        }
    }

    @Nested
    inner class ServerCommand {
        @Test
        fun `server with no port uses the default port`() {
            assertEquals(Mode.Server(), parse("server").mode)
        }

        @Test
        fun `server --port N uses the given port`() {
            assertEquals(Mode.Server(port = 9000), parse("server", "--port", "9000").mode)
        }

        @Test
        fun `server --port with a non-integer value throws`() {
            failing("server", "--port", "nope")
        }

        @Test
        fun `server --port with no value throws`() {
            failing("server", "--port")
        }

        @Test
        fun `server with an unknown trailing flag throws`() {
            failing("server", "--bogus")
        }

        @Test
        fun `server --map and --unit resolve to Server`() {
            assertEquals(
                Mode.Server(setup = Setup(mapName = "river-valley", unitsName = "duel")),
                parse("server", "--map", "river-valley", "--unit", "duel").mode,
            )
        }

        @Test
        fun `server accepts --theme but it has no effect`() {
            val launch = parse("--theme", "dark", "server")
            assertEquals(Mode.Server(), launch.mode)
            assertEquals("dark", launch.themeName)
        }
    }

    @Nested
    inner class UnknownInput {
        @Test
        fun `unknown leading flag throws`() {
            val ex = failing("--nonsense", "hot-seat")
            assertTrue(ex.output.contains("--nonsense"))
        }
    }

    @Nested
    inner class ThemeOption {
        @Test
        fun `no --theme resolves to auto (null)`() {
            assertEquals(null, parse("hot-seat").themeName)
            assertEquals(null, parse("host").themeName)
            assertEquals(null, parse("join", "192.168.1.5", "--session", "ABC123").themeName)
        }

        @Test
        fun `--theme before hot-seat is carried on the Launch, unvalidated`() {
            assertEquals("dark", parse("--theme", "dark", "hot-seat").themeName)
            assertEquals("not-a-real-theme", parse("--theme", "not-a-real-theme", "hot-seat").themeName)
        }

        @Test
        fun `--theme before host is carried on the Launch`() {
            assertEquals("dark", parse("--theme", "dark", "host").themeName)
        }

        @Test
        fun `--theme before join is carried on the Launch`() {
            val launch = parse("--theme", "light", "join", "192.168.1.5", "--session", "ABC123")
            assertEquals("light", launch.themeName)
            assertEquals(Mode.Join(host = "192.168.1.5", sessionId = "ABC123"), launch.mode)
        }

        @Test
        fun `--theme with no value throws`() {
            val ex = failing("--theme")
            assertTrue(ex.output.contains("--theme"))
        }

        @Test
        fun `--theme=light is accepted (Clikt supports the = form)`() {
            assertEquals("light", parse("--theme=light", "hot-seat").themeName)
        }

        @Test
        fun `repeated --theme takes the last value`() {
            assertEquals("light", parse("--theme", "dark", "--theme", "light", "hot-seat").themeName)
        }

        @Test
        fun `--theme after the subcommand is rejected -- root options must come first`() {
            val ex = failing("hot-seat", "--theme", "dark")
            assertTrue(ex.output.contains("--theme"))
        }
    }

    @Nested
    inner class RootOptionOrdering {
        @Test
        fun `--add-map before hot-seat registers it`() {
            val launch = parse("--add-map", "x.json", "hot-seat")
            assertEquals(listOf("x.json"), launch.mapPaths)
        }

        @Test
        fun `repeated --add-map preserves every registration in order`() {
            val launch = parse("--add-map", "one.json", "--add-map", "two.json", "hot-seat")
            assertEquals(listOf("one.json", "two.json"), launch.mapPaths)
        }

        @Test
        fun `repeated --add-mech preserves every registration in order`() {
            val launch = parse("--add-mech", "one.json", "--add-mech", "two.json", "hot-seat")
            assertEquals(listOf("one.json", "two.json"), launch.mechPaths)
        }

        @Test
        fun `repeated --add-unit preserves every registration in order`() {
            val launch = parse("--add-unit", "one.json", "--add-unit", "two.json", "hot-seat")
            assertEquals(listOf("one.json", "two.json"), launch.unitPaths)
        }

        @Test
        fun `--add-map after the subcommand is rejected -- root options must come first`() {
            val ex = failing("hot-seat", "--add-map", "x.json")
            assertTrue(ex.output.contains("--add-map"))
        }

        @Test
        fun `--add-unit after the subcommand is rejected -- root options must come first`() {
            val ex = failing("hot-seat", "--add-unit", "x.json")
            assertTrue(ex.output.contains("--add-unit"))
        }

        @Test
        fun `hot-seat's own --map is accepted after the subcommand name`() {
            assertEquals(Mode.HotSeat(Setup(mapName = "river-valley")), parse("hot-seat", "--map", "river-valley").mode)
        }
    }

    @Nested
    inner class SubcommandDispatch {
        @Test
        fun `an unknown subcommand throws`() {
            failing("bogus")
        }

        @Test
        fun `hot-seat, host, join and server are all listed`() {
            val help = failing("--help").output

            assertTrue(help.contains("hot-seat"))
            assertTrue(help.contains("host"))
            assertTrue(help.contains("join"))
            assertTrue(help.lines().any { it.trimStart().startsWith("server ") })
            assertTrue(help.lines().none { it.trimStart().startsWith("serve ") })
        }

        @Test
        fun `serve is rejected as an unknown subcommand`() {
            failing("serve")
        }
    }

    @Nested
    inner class ListFlags {
        @Test
        fun `--list-maps prints built-in maps and exits 0`() {
            val result = failing("--list-maps", "hot-seat")
            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("Maps:"))
            assertTrue(result.output.contains("battletech-classic"))
        }

        @Test
        fun `--list-mechs prints the collection hierarchy`() {
            val output = failing("--list-mechs", "hot-seat").output
            assertTrue(output.contains("Mechs:"))
            assertTrue(output.contains("classic:"))
            assertTrue(output.contains("AS7-D"))
        }

        @Test
        fun `--list-units prints the collection hierarchy`() {
            val output = failing("--list-units", "hot-seat").output
            assertTrue(output.contains("Units:"))
            assertTrue(output.contains("default:"))
            assertTrue(output.contains("A1"))
        }

        @Test
        fun `--list-themes prints built-in themes`() {
            assertTrue(failing("--list-themes", "hot-seat").output.contains("dark"))
        }

        @Test
        fun `combining list flags prints every section, maps before mechs before units`() {
            val output = failing("--list-units", "--list-mechs", "--list-maps", "hot-seat").output
            assertTrue(output.contains("Maps:"))
            assertTrue(output.contains("Mechs:"))
            assertTrue(output.contains("Units:"))
            assertTrue(output.indexOf("Maps:") < output.indexOf("Mechs:"))
            assertTrue(output.indexOf("Mechs:") < output.indexOf("Units:"))
        }

        @Test
        fun `listing works with no subcommand at all -- eager options finalize before the missing-subcommand check`() {
            val result = failing("--list-maps")
            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("battletech-classic"))
        }

        @Test
        fun `join --list-themes lists themes despite a missing ADDRESS and --session`() {
            val result = failing("--list-themes", "join")
            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("Themes:"))
            assertTrue(result.output.contains("dark"))
        }

        @Test
        fun `external map is tagged and listed after built-ins`(@TempDir tempDir: Path) {
            val custom = tempDir.resolve("custom.json")
            custom.writeText("""{"width":1,"height":1,"hexes":[]}""")

            val output = failing("--add-map", custom.toString(), "--list-maps", "hot-seat").output

            assertTrue(output.contains("battletech-classic"))
            assertTrue(output.contains("custom (external)"))
            assertTrue(output.indexOf("battletech-classic") < output.indexOf("custom (external)"))
        }

        @Test
        fun `external mech collection is tagged and lists its variants`(@TempDir tempDir: Path) {
            val custom = tempDir.resolve("my-lance.json")
            custom.writeText(
                """
                {"models":[{
                  "variant":"CPLT-C1",
                  "name":"Catapult CPLT-C1",
                  "tonnage":20,
                  "walkingMP":4,
                  "runningMP":6,
                  "armor":{
                    "head":0,"centerTorso":0,"centerTorsoRear":0,
                    "leftTorso":0,"leftTorsoRear":0,"rightTorso":0,"rightTorsoRear":0,
                    "leftArm":0,"rightArm":0,"leftLeg":0,"rightLeg":0
                  },
                  "loadout":[]
                }]}
                """.trimIndent(),
            )

            val output = failing("--add-mech", custom.toString(), "--list-mechs", "hot-seat").output

            assertTrue(output.contains("classic:"))
            assertTrue(output.contains("my-lance (external):"))
            assertTrue(output.contains("CPLT-C1"))
        }

        @Test
        fun `external unit collection is tagged and lists its unit ids`(@TempDir tempDir: Path) {
            val custom = tempDir.resolve("duel.json")
            custom.writeText(
                """{"units":[{"id":"X1","player":1,"variant":"LCT-1V","gunnerySkill":4,"pilotingSkill":5,
                  "position":{"col":1,"row":1},"facing":"N"}]}""",
            )

            val output = failing("--add-unit", custom.toString(), "--list-units", "hot-seat").output

            assertTrue(output.contains("default:"))
            assertTrue(output.contains("duel (external):"))
            assertTrue(output.contains("X1"))
        }

        @Test
        fun `a bad external mech path fails with the loader's own message`(@TempDir tempDir: Path) {
            val missing = tempDir.resolve("does-not-exist.json")

            val result = failing("--add-mech", missing.toString(), "--list-mechs", "hot-seat")

            assertEquals(2, result.statusCode)
        }

        @Test
        fun `a duplicate mech variant across two registrations fails listing exactly as it would fail launch`(
            @TempDir tempDir: Path,
        ) {
            val model = """
                {"variant":"AS7-D","name":"Dup","tonnage":20,"walkingMP":4,"runningMP":6,
                  "armor":{"head":0,"centerTorso":0,"centerTorsoRear":0,"leftTorso":0,"leftTorsoRear":0,
                  "rightTorso":0,"rightTorsoRear":0,"leftArm":0,"rightArm":0,"leftLeg":0,"rightLeg":0}}
            """.trimIndent()
            val dup = tempDir.resolve("dup.json")
            dup.writeText("""{"models":[$model]}""")

            val result = failing("--add-mech", dup.toString(), "--list-mechs", "hot-seat")

            assertEquals(2, result.statusCode)
        }
    }

    @Nested
    inner class HelpAndExitCodes {
        @Test
        fun `--help carries status code 0`() {
            assertEquals(0, failing("--help").statusCode)
        }

        @Test
        fun `a usage error carries Clikt's own status code 1`() {
            assertEquals(1, failing("--bogus").statusCode)
        }

        @Test
        fun `host --help renders host's own options`() {
            val help = failing("host", "--help").output
            assertTrue(help.contains("--port"))
            assertTrue(help.contains("--map"))
            assertTrue(help.contains("--unit"))
        }

        @Test
        fun `root help is a dispatcher overview listing every registration and listing option`() {
            val help = failing("--help").output

            assertTrue(help.contains("hot-seat"))
            assertTrue(help.contains("--add-map"))
            assertTrue(help.contains("--add-mech"))
            assertTrue(help.contains("--add-unit"))
            assertTrue(help.contains("--theme"))
            assertTrue(help.contains("--list-maps"))
            assertTrue(help.contains("--list-mechs"))
            assertTrue(help.contains("--list-units"))
            assertTrue(help.contains("--list-themes"))
        }

        @Test
        fun `root help explains how to show command-specific help`() {
            // Mordant soft-wraps the epilog at terminal width, so normalize wrap points back to
            // spaces before checking — this sentence is logically one line regardless of width.
            val help = failing("--help").output.replace("\n", " ")

            assertTrue(help.contains("Run 'battletech-tui <command> --help' for command-specific help."))
        }

        @Test
        fun `root help explains that root options must precede the command name`() {
            val help = failing("--help").output.replace("\n", " ")

            assertTrue(help.contains("must come BEFORE the command name"))
        }

        @Test
        fun `hot-seat --help renders only hot-seat's own map and unit selection`() {
            val help = failing("hot-seat", "--help").output

            assertTrue(help.contains("--map"))
            assertTrue(help.contains("--unit"))
            assertTrue(!help.contains("--add-map"))
            assertTrue(!help.contains("--list-maps"))
        }

        @Test
        fun `join --help does not mention --map or --unit`() {
            val help = failing("join", "--help").output
            assertTrue(!help.contains("--map"))
            assertTrue(!help.contains("--unit"))
        }

        @Test
        fun `a usage error message includes the failing subcommand's usage`() {
            val message = failing("host", "--port", "nope").output
            assertTrue(message.contains("host"))
        }
    }
}
