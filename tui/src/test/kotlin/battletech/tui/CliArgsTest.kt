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

    private fun parse(vararg args: String): Mode {
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
    inner class HotSeat {
        @Test
        fun `no args resolves to HotSeat`() {
            assertEquals(Mode.HotSeat(), parse())
        }

        @Test
        fun `explicit hot-seat is equivalent to the bare invocation`() {
            assertEquals(parse(), parse("hot-seat"))
        }

        @Test
        fun `hot-seat --game name resolves to HotSeat with gameName`() {
            assertEquals(Mode.HotSeat(gameName = "name"), parse("hot-seat", "--game", "name"))
        }

        @Test
        fun `hot-seat --map path registers external map`() {
            assertEquals(Mode.HotSeat(mapPaths = listOf("arena.json")), parse("hot-seat", "--map", "arena.json"))
        }

        @Test
        fun `hot-seat repeated --map preserves every registration in order`() {
            assertEquals(
                Mode.HotSeat(mapPaths = listOf("one.json", "two.json")),
                parse("hot-seat", "--map", "one.json", "--map", "two.json"),
            )
        }

        @Test
        fun `hot-seat --map with no value throws`() {
            failing("hot-seat", "--map")
        }

        @Test
        fun `hot-seat repeated --mech preserves every collection in order`() {
            assertEquals(
                Mode.HotSeat(mechPaths = listOf("custom.json", "more.json")),
                parse("hot-seat", "--mech", "custom.json", "--mech", "more.json"),
            )
        }
    }

    @Nested
    inner class HostCommand {
        @Test
        fun `host with no port uses the default port`() {
            assertEquals(Mode.Host(), parse("host"))
        }

        @Test
        fun `host --port N uses the given port`() {
            assertEquals(Mode.Host(port = 5555), parse("host", "--port", "5555"))
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
        fun `host --game name and maps resolve to Host`() {
            assertEquals(
                Mode.Host(gameName = "game", mapPaths = listOf("one.json", "two.json")),
                parse("host", "--game", "game", "--map", "one.json", "--map", "two.json"),
            )
        }

        @Test
        fun `host accepts repeated mech collections`() {
            assertEquals(
                Mode.Host(mechPaths = listOf("one.json", "two.json")),
                parse("host", "--mech", "one.json", "--mech", "two.json"),
            )
        }
    }

    @Nested
    inner class JoinCommand {
        @Test
        fun `join ip --session id resolves with the default port`() {
            val mode = parse("join", "192.168.1.5", "--session", "ABC123")
            assertEquals(Mode.Join(host = "192.168.1.5", sessionId = "ABC123"), mode)
        }

        @Test
        fun `join ip colon port --session id splits host and port`() {
            val mode = parse("join", "192.168.1.5:9999", "--session", "ABC123")
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
        fun `join has no --game -- the game comes from the host`() {
            val ex = failing("join", "192.168.1.5", "--session", "s", "--game", "x")
            assertTrue(ex.output.contains("--game"))
        }

        @Test
        fun `join has no --mech -- the models come from the host`() {
            val ex = failing("join", "192.168.1.5", "--session", "s", "--mech", "x")
            assertTrue(ex.output.contains("--mech"))
        }
    }

    @Nested
    inner class ServerCommand {
        @Test
        fun `server with no port uses the default port`() {
            assertEquals(Mode.Server(), parse("server"))
        }

        @Test
        fun `server --port N uses the given port`() {
            assertEquals(Mode.Server(port = 9000), parse("server", "--port", "9000"))
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
        fun `server --game name and maps resolve to Server`() {
            assertEquals(
                Mode.Server(gameName = "game", mapPaths = listOf("arena.json")),
                parse("server", "--game", "game", "--map", "arena.json"),
            )
        }

        @Test
        fun `server accepts mech collections`() {
            assertEquals(
                Mode.Server(mechPaths = listOf("custom.json")),
                parse("server", "--mech", "custom.json"),
            )
        }

        @Test
        fun `server has no --theme`() {
            val ex = failing("server", "--theme", "dark")
            assertTrue(ex.output.contains("--theme"))
        }
    }

    @Nested
    inner class UnknownInput {
        @Test
        fun `unknown leading flag throws`() {
            val ex = failing("--nonsense")
            assertTrue(ex.output.contains("--nonsense"))
        }
    }

    @Nested
    inner class ThemeOption {
        @Test
        fun `no --theme resolves to auto (null) for hot-seat, host, and join`() {
            assertEquals(null, (parse() as Mode.HotSeat).themeName)
            assertEquals(null, (parse("host") as Mode.Host).themeName)
            assertEquals(null, (parse("join", "192.168.1.5", "--session", "ABC123") as Mode.Join).themeName)
        }

        @Test
        fun `hot-seat --theme name resolves to HotSeat with themeName, unvalidated`() {
            assertEquals(Mode.HotSeat(themeName = "dark"), parse("hot-seat", "--theme", "dark"))
            assertEquals(
                Mode.HotSeat(themeName = "not-a-real-theme"),
                parse("hot-seat", "--theme", "not-a-real-theme"),
            )
        }

        @Test
        fun `--theme may appear after host`() {
            assertEquals(Mode.Host(themeName = "dark"), parse("host", "--theme", "dark"))
        }

        @Test
        fun `--theme may appear after join`() {
            val expected = Mode.Join(host = "192.168.1.5", sessionId = "ABC123", themeName = "light")
            assertEquals(expected, parse("join", "192.168.1.5", "--session", "ABC123", "--theme", "light"))
        }

        @Test
        fun `hot-seat --theme with no value throws`() {
            val ex = failing("hot-seat", "--theme")
            assertTrue(ex.output.contains("--theme"))
        }

        @Test
        fun `hot-seat --theme=light is accepted (Clikt supports the = form)`() {
            assertEquals(Mode.HotSeat(themeName = "light"), parse("hot-seat", "--theme=light"))
        }

        @Test
        fun `hot-seat repeated --theme takes the last value`() {
            assertEquals(
                Mode.HotSeat(themeName = "light"),
                parse("hot-seat", "--theme", "dark", "--theme", "light"),
            )
        }
    }

    @Nested
    inner class RootOptions {
        @Test
        fun `root has no configurable options`() {
            val ex = failing("--map", "x")

            assertTrue(ex.output.contains("--map"))
        }

        @Test
        fun `root rejects theme option`() {
            val ex = failing("--theme", "dark")

            assertTrue(ex.output.contains("--theme"))
        }

        @Test
        fun `root rejects listing options`() {
            val ex = failing("--list-maps")

            assertTrue(ex.output.contains("--list-maps"))
        }

        @Test
        fun `root rejects options before a subcommand`() {
            val ex = failing("--game", "x", "host")

            assertTrue(ex.output.contains("--game"))
        }

        @Test
        fun `configured hot-seat options are accepted after the subcommand`() {
            assertEquals(Mode.HotSeat(mapPaths = listOf("x")), parse("hot-seat", "--map", "x"))
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
        fun `hot-seat is accepted as an explicit subcommand`() {
            assertEquals(Mode.HotSeat(), parse("hot-seat"))
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
            val result = failing("hot-seat", "--list-maps")
            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("Maps:"))
            assertTrue(result.output.contains("battletech-classic"))
        }

        @Test
        fun `--list-mechs prints the collection hierarchy`() {
            val output = failing("hot-seat", "--list-mechs").output
            assertTrue(output.contains("Mechs:"))
            assertTrue(output.contains("classic:"))
            assertTrue(output.contains("AS7-D"))
        }

        @Test
        fun `--list-games prints built-in games`() {
            assertTrue(failing("hot-seat", "--list-games").output.contains("default"))
        }

        @Test
        fun `--list-themes prints built-in themes`() {
            assertTrue(failing("hot-seat", "--list-themes").output.contains("dark"))
        }

        @Test
        fun `combining list flags prints both sections, maps before mechs`() {
            val output = failing("hot-seat", "--list-mechs", "--list-maps").output
            assertTrue(output.contains("Maps:"))
            assertTrue(output.contains("Mechs:"))
            assertTrue(output.indexOf("Maps:") < output.indexOf("Mechs:"))
        }

        @Test
        fun `hot-seat --list-maps behaves like other content commands`() {
            assertTrue(failing("hot-seat", "--list-maps").output.contains("battletech-classic"))
        }

        @Test
        fun `server --help does not mention --list-themes`() {
            assertTrue(!failing("server", "--help").output.contains("--list-themes"))
        }

        @Test
        fun `join --help does not mention --list-maps`() {
            assertTrue(!failing("join", "--help").output.contains("--list-maps"))
        }

        @Test
        fun `join --list-themes lists themes despite a missing ADDRESS and --session`() {
            val result = failing("join", "--list-themes")
            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("Themes:"))
            assertTrue(result.output.contains("dark"))
        }

        @Test
        fun `external map is tagged and listed after built-ins`(@TempDir tempDir: Path) {
            val custom = tempDir.resolve("custom.json")
            custom.writeText("""{"width":1,"height":1,"hexes":[]}""")

            val output = failing("hot-seat", "--map", custom.toString(), "--list-maps").output

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

            val output = failing("hot-seat", "--mech", custom.toString(), "--list-mechs").output

            assertTrue(output.contains("classic:"))
            assertTrue(output.contains("my-lance (external):"))
            assertTrue(output.contains("CPLT-C1"))
        }

        @Test
        fun `a bad external mech path fails with the loader's own message`(@TempDir tempDir: Path) {
            val missing = tempDir.resolve("does-not-exist.json")

            val result = failing("hot-seat", "--mech", missing.toString(), "--list-mechs")

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
            assertTrue(help.contains("--game"))
            assertTrue(help.contains("--map"))
            assertTrue(help.contains("--mech"))
            assertTrue(help.contains("--theme"))
            assertTrue(help.contains("default"))
        }

        @Test
        fun `root help is a dispatcher overview without mode options`() {
            val help = failing("--help").output

            assertTrue(help.contains("hot-seat"))
            assertTrue(!help.contains("--game"))
            assertTrue(!help.contains("--map"))
            assertTrue(!help.contains("--theme"))
        }

        @Test
        fun `root help explains how to show command-specific help`() {
            val help = failing("--help").output

            assertTrue(help.contains("Run 'battletech-tui <command> --help' for command-specific help."))
        }

        @Test
        fun `hot-seat --help renders hot-seat options`() {
            val help = failing("hot-seat", "--help").output

            assertTrue(help.contains("--game"))
            assertTrue(help.contains("--map"))
            assertTrue(help.contains("--mech"))
            assertTrue(help.contains("--theme"))
            assertTrue(help.contains("--list-maps"))
            assertTrue(help.contains("--list-themes"))
        }

        @Test
        fun `join --help does not mention --map`() {
            val help = failing("join", "--help").output
            assertTrue(!help.contains("--map"))
            assertTrue(!help.contains("--game"))
            assertTrue(!help.contains("--mech"))
        }

        @Test
        fun `server --help does not mention --theme`() {
            assertTrue(!failing("server", "--help").output.contains("--theme"))
        }

        @Test
        fun `a usage error message includes the failing subcommand's usage`() {
            val message = failing("host", "--port", "nope").output
            assertTrue(message.contains("host"))
        }
    }
}
