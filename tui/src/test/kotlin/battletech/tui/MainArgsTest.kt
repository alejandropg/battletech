package battletech.tui

import battletech.tui.screen.TuiTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MainArgsTest {

    @Nested
    inner class LocalMode {
        @Test
        fun `no args resolves to Local`() {
            assertEquals(Mode.Local(), parseArgs(emptyArray()))
        }

        @Test
        fun `--map name resolves to Local with mapName`() {
            val mode = parseArgs(arrayOf("--map", "name"))
            assertEquals(Mode.Local(mapName = "name"), mode)
        }

        @Test
        fun `--map with no value throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--map"))
            }
        }
    }

    @Nested
    inner class HostMode {
        @Test
        fun `--host with no port uses the default port`() {
            val mode = parseArgs(arrayOf("--host"))
            assertEquals(Mode.Host(port = DEFAULT_PORT), mode)
        }

        @Test
        fun `--host --port N uses the given port`() {
            val mode = parseArgs(arrayOf("--host", "--port", "5555"))
            assertEquals(Mode.Host(port = 5555), mode)
        }

        @Test
        fun `--host --port with a non-integer value throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--host", "--port", "nope"))
            }
        }

        @Test
        fun `--host --port with no value throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--host", "--port"))
            }
        }

        @Test
        fun `--host with an unknown trailing flag throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--host", "--bogus"))
            }
        }

        @Test
        fun `--host --map name resolves to Host with mapName`() {
            val mode = parseArgs(arrayOf("--host", "--map", "name"))
            assertEquals(Mode.Host(port = DEFAULT_PORT, mapName = "name"), mode)
        }

        @Test
        fun `--map name --host resolves to Host with mapName`() {
            val mode = parseArgs(arrayOf("--map", "name", "--host"))
            assertEquals(Mode.Host(port = DEFAULT_PORT, mapName = "name"), mode)
        }
    }

    @Nested
    inner class JoinMode {
        @Test
        fun `--join ip --session id resolves with the default port`() {
            val mode = parseArgs(arrayOf("--join", "192.168.1.5", "--session", "ABC123"))
            assertEquals(Mode.Join(host = "192.168.1.5", port = DEFAULT_PORT, sessionId = "ABC123"), mode)
        }

        @Test
        fun `--join ip colon port --session id splits host and port`() {
            val mode = parseArgs(arrayOf("--join", "192.168.1.5:9999", "--session", "ABC123"))
            assertEquals(Mode.Join(host = "192.168.1.5", port = 9999, sessionId = "ABC123"), mode)
        }

        @Test
        fun `--join with no host throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--join"))
            }
        }

        @Test
        fun `--join with no --session throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--join", "192.168.1.5"))
            }
        }

        @Test
        fun `--join --session with no id value throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--join", "192.168.1.5", "--session"))
            }
        }

        @Test
        fun `--join with malformed host colon port throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--join", "192.168.1.5:notaport", "--session", "ABC123"))
            }
        }

        @Test
        fun `--join with empty host before the colon throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--join", ":9999", "--session", "ABC123"))
            }
        }

        @Test
        fun `--join with --map throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--join", "192.168.1.5", "--session", "s", "--map", "x"))
            }
        }
    }

    @Nested
    inner class ServerMode {
        @Test
        fun `--server with no port uses the default port`() {
            val mode = parseArgs(arrayOf("--server"))
            assertEquals(Mode.Server(port = DEFAULT_PORT), mode)
        }

        @Test
        fun `--server --port N uses the given port`() {
            val mode = parseArgs(arrayOf("--server", "--port", "9000"))
            assertEquals(Mode.Server(port = 9000), mode)
        }

        @Test
        fun `--server --port with a non-integer value throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--server", "--port", "nope"))
            }
        }

        @Test
        fun `--server --port with no value throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--server", "--port"))
            }
        }

        @Test
        fun `--server with an unknown trailing flag throws`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--server", "--bogus"))
            }
        }

        @Test
        fun `--server --map name resolves to Server with mapName`() {
            val mode = parseArgs(arrayOf("--server", "--map", "name"))
            assertEquals(Mode.Server(port = DEFAULT_PORT, mapName = "name"), mode)
        }

        @Test
        fun `--map name --server resolves to Server with mapName`() {
            val mode = parseArgs(arrayOf("--map", "name", "--server"))
            assertEquals(Mode.Server(port = DEFAULT_PORT, mapName = "name"), mode)
        }
    }

    @Nested
    inner class MalformedGlobal {
        @Test
        fun `unknown leading flag throws`() {
            val ex = assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--nonsense"))
            }
            assertTrue(ex.message!!.contains("--nonsense"))
        }
    }

    @Nested
    inner class ThemeFlag {
        @Test
        fun `no --theme resolves to auto (null) for local, host, and join`() {
            assertEquals(null, parseArgs(emptyArray()).let { (it as Mode.Local).theme })
            assertEquals(null, parseArgs(arrayOf("--host")).let { (it as Mode.Host).theme })
            assertEquals(
                null,
                parseArgs(arrayOf("--join", "192.168.1.5", "--session", "ABC123")).let { (it as Mode.Join).theme },
            )
        }

        @Test
        fun `each of the six theme names resolves to its TuiTheme`() {
            val expected = mapOf(
                "dark" to TuiTheme.DARK,
                "light" to TuiTheme.LIGHT,
                "dark-256" to TuiTheme.DARK_256,
                "light-256" to TuiTheme.LIGHT_256,
                "dark-16" to TuiTheme.DARK_16,
                "light-16" to TuiTheme.LIGHT_16,
            )
            for ((flag, theme) in expected) {
                assertEquals(Mode.Local(theme = theme), parseArgs(arrayOf("--theme", flag)))
            }
        }

        @Test
        fun `--theme may appear before or after --host`() {
            assertEquals(Mode.Host(port = DEFAULT_PORT, theme = TuiTheme.DARK), parseArgs(arrayOf("--host", "--theme", "dark")))
            assertEquals(Mode.Host(port = DEFAULT_PORT, theme = TuiTheme.DARK), parseArgs(arrayOf("--theme", "dark", "--host")))
        }

        @Test
        fun `--theme may appear before or after --join`() {
            val expected = Mode.Join(host = "192.168.1.5", port = DEFAULT_PORT, sessionId = "ABC123", theme = TuiTheme.LIGHT)
            assertEquals(expected, parseArgs(arrayOf("--join", "192.168.1.5", "--session", "ABC123", "--theme", "light")))
            assertEquals(expected, parseArgs(arrayOf("--theme", "light", "--join", "192.168.1.5", "--session", "ABC123")))
        }

        @Test
        fun `--theme with no value throws`() {
            val ex = assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--theme"))
            }
            assertEquals("--theme requires a value", ex.message)
        }

        @Test
        fun `--theme with an unrecognized value throws`() {
            val ex = assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--theme", "bogus"))
            }
            assertEquals("Unknown theme: bogus (expected dark, light, dark-256, light-256, dark-16 or light-16)", ex.message)
        }

        @Test
        fun `--theme is case-sensitive`() {
            val ex = assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--theme", "Dark"))
            }
            assertEquals("Unknown theme: Dark (expected dark, light, dark-256, light-256, dark-16 or light-16)", ex.message)
        }

        @Test
        fun `--theme=light is not supported`() {
            assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--theme=light"))
            }
        }

        @Test
        fun `a repeated --theme throws Unknown argument, matching --map's duplicate behavior`() {
            val ex = assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--theme", "dark", "--theme", "light"))
            }
            assertEquals("Unknown argument: --theme", ex.message)
        }

        @Test
        fun `--theme cannot be combined with --server, in either order`() {
            val afterServer = assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--server", "--theme", "dark"))
            }
            assertEquals("--theme cannot be combined with --server", afterServer.message)

            val beforeServer = assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--theme", "dark", "--server"))
            }
            assertEquals("--theme cannot be combined with --server", beforeServer.message)
        }

        @Test
        fun `--theme immediately followed by --server consumes --server as the (invalid) theme value`() {
            // Known ordering quirk of the extract-first-occurrence strategy: with no value between
            // them, --theme greedily takes --server as its value before the --server/--theme
            // incompatibility check ever runs. See Main.kt's parseArgs KDoc.
            val ex = assertThrows(ArgsException::class.java) {
                parseArgs(arrayOf("--theme", "--server"))
            }
            assertEquals("Unknown theme: --server (expected dark, light, dark-256, light-256, dark-16 or light-16)", ex.message)
        }
    }
}
