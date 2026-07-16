package battletech.network.transport

import battletech.network.PipedConnection
import battletech.network.wire.ClientMessage
import battletech.network.wire.ServerMessage
import battletech.tactical.model.PlayerId
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.StandUp
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Drives [JsonLineConnection.Server]/[JsonLineConnection.Client] over a real [PipedConnection] — no sockets, but real line-oriented JSON on the wire. */
internal class JsonLineConnectionTest {

    @Test
    fun `round-trips a real SubmitCommand from client to server, and a real CommandReply from server to client`() {
        val pipes = PipedConnection()
        val server = JsonLineConnection.Server(pipes.serverInput, pipes.serverOutput)
        val client = JsonLineConnection.Client(pipes.clientInput, pipes.clientOutput)

        val command = ClientMessage.SubmitCommand(7L, StandUp(PlayerId.PLAYER_1, UnitId("unit-1")))
        client.send(command)
        assertThat(server.receive()).isEqualTo(command)

        val reply = ServerMessage.CommandReply(7L, CommandResult.Rejected(CommandRejection.OpponentUnavailable))
        server.send(reply)
        assertThat(client.receive()).isEqualTo(reply)
    }

    @Test
    fun `receive() returns null at end of stream`() {
        val pipes = PipedConnection()
        val client = JsonLineConnection.Client(pipes.clientInput, pipes.clientOutput)

        pipes.closeServerSide()

        assertThat(client.receive()).isNull()
    }

    @Test
    fun `receive() returns null at end of stream from the server's side too`() {
        val pipes = PipedConnection()
        val server = JsonLineConnection.Server(pipes.serverInput, pipes.serverOutput)

        pipes.closeClientSide()

        assertThat(server.receive()).isNull()
    }

    // ---------- own-close unblocking an own-blocked receive(): needs a REAL socket, not PipedConnection ----------
    //
    // PipedConnection's two directions are independent PipedInputStream/PipedOutputStream pairs, not
    // two views of one shared fd, so closing "my write end" there can never reach a thread blocked
    // reading "my read end" — see PipedConnection's own KDoc. A real Socket doesn't have that split:
    // closing its OutputStream closes the whole socket (both directions), which is exactly the
    // mechanism close()'s output-then-input order (see JsonLineConnection's class KDoc) depends on.
    // This test is therefore deliberately socket-backed rather than piped (see realJsonLineSocketPair).

    @Test
    fun `close() ALSO unblocks the closing endpoint's own receive(), returning null just like a clean EOF`() {
        val (connections, cleanup) = realJsonLineSocketPair()
        val (server, _) = connections
        cleanup.use {
            val result = AtomicReference<ClientMessage?>(null)
            val reader = thread(isDaemon = true, name = "test-own-close-reader") {
                result.set(server.receive())
            }
            // The blocked native socket read reports as RUNNABLE, not WAITING (unlike the
            // in-memory adapter's queue.take()) — give it a moment to actually enter the read
            // rather than polling for a thread state that will never come.
            Thread.sleep(200)

            server.close()
            reader.join(3_000)

            assertThat(reader.isAlive).isFalse()
            // Closing out from under your own blocked readLine() now surfaces as a plain null,
            // the same as a clean peer-initiated EOF — receive() catches the underlying
            // IOException internally (see JsonLineConnection's class KDoc) so this class matches
            // InMemoryConnection's close semantics exactly, rather than leaking a
            // socket-specific exception a caller would have to know to catch.
            assertThat(result.get()).isNull()
        }
    }
}
