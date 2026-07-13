package battletech.tui

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
}
