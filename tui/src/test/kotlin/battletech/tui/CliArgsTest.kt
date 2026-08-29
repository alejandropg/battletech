package battletech.tui

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
        fun `no args resolves to Local`() {
            assertEquals(Mode.Local(), parse())
        }

        @Test
        fun `--map name resolves to Local with mapName`() {
            assertEquals(Mode.Local(mapName = "name"), parse("--map", "name"))
        }

        @Test
        fun `--map with no value throws`() {
            failing("--map")
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
        fun `host --map name resolves to Host with mapName`() {
            assertEquals(Mode.Host(mapName = "name"), parse("host", "--map", "name"))
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
        fun `server --map name resolves to Server with mapName`() {
            assertEquals(Mode.Server(mapName = "name"), parse("server", "--map", "name"))
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
            assertEquals(null, (parse() as Mode.Local).themeName)
            assertEquals(null, (parse("host") as Mode.Host).themeName)
            assertEquals(null, (parse("join", "192.168.1.5", "--session", "ABC123") as Mode.Join).themeName)
        }

        @Test
        fun `--theme name resolves to Local with themeName, unvalidated (like --map)`() {
            assertEquals(Mode.Local(themeName = "dark"), parse("--theme", "dark"))
            assertEquals(Mode.Local(themeName = "not-a-real-theme"), parse("--theme", "not-a-real-theme"))
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
        fun `--theme with no value throws`() {
            val ex = failing("--theme")
            assertTrue(ex.output.contains("--theme"))
        }

        @Test
        fun `--theme=light is accepted (Clikt supports the = form)`() {
            assertEquals(Mode.Local(themeName = "light"), parse("--theme=light"))
        }

        @Test
        fun `a repeated --theme takes the last value`() {
            assertEquals(Mode.Local(themeName = "light"), parse("--theme", "dark", "--theme", "light"))
        }
    }

    /**
     * `--map`/`--theme` live on the root so the bare hot-seat form keeps taking them directly
     * (see [parseArgs]'s KDoc), but that means they must come AFTER the subcommand name — giving
     * them before it would otherwise silently set the wrong (root) copy, so the root command
     * guards against that explicitly rather than letting it degrade to "option ignored".
     */
    @Nested
    inner class RootOptionPlacement {
        @Test
        fun `--map before the host subcommand is rejected`() {
            val ex = failing("--map", "x", "host")
            assertTrue(ex.output.contains("--map"))
            assertTrue(ex.output.contains("host"))
        }

        @Test
        fun `--map before the server subcommand is rejected`() {
            val ex = failing("--map", "x", "server")
            assertTrue(ex.output.contains("--map"))
            assertTrue(ex.output.contains("server"))
        }

        @Test
        fun `--theme before the host subcommand is rejected`() {
            val ex = failing("--theme", "dark", "host")
            assertTrue(ex.output.contains("--theme"))
            assertTrue(ex.output.contains("host"))
        }

        @Test
        fun `--theme before the join subcommand is rejected`() {
            val ex = failing("--theme", "light", "join", "192.168.1.5", "--session", "ABC123")
            assertTrue(ex.output.contains("--theme"))
            assertTrue(ex.output.contains("join"))
        }

        @Test
        fun `both --map and --theme before a subcommand are named in one error`() {
            val ex = failing("--map", "x", "--theme", "dark", "host")
            assertTrue(ex.output.contains("--map"))
            assertTrue(ex.output.contains("--theme"))
        }

        @Test
        fun `--map after the host subcommand is accepted`() {
            assertEquals(Mode.Host(mapName = "x"), parse("host", "--map", "x"))
        }
    }

    @Nested
    inner class SubcommandDispatch {
        @Test
        fun `an unknown subcommand throws`() {
            failing("bogus")
        }

        @Test
        fun `host, join and server are all listed`() {
            val help = failing("--help").output
            assertTrue(help.contains("host"))
            assertTrue(help.contains("join"))
            assertTrue(help.lines().any { it.trimStart().startsWith("server ") })
            assertTrue(help.lines().none { it.trimStart().startsWith("serve ") })
        }

        @Test
        fun `serve is rejected as an unknown subcommand`() {
            failing("serve")
        }

        @Test
        fun `--map host is a hot-seat map named host, not the host subcommand`() {
            assertEquals(Mode.Local(mapName = "host"), parse("--map", "host"))
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
            assertTrue(help.contains("--theme"))
            assertTrue(help.contains("battletech-classic"))
        }

        @Test
        fun `join --help does not mention --map`() {
            assertTrue(!failing("join", "--help").output.contains("--map"))
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
