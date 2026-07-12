package battletech.network

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.Writer

/**
 * A pair of in-memory pipes wiring a "server" side to a "client" side, used
 * by protocol tests to drive [battletech.network.server.GameServer] and
 * [battletech.network.client.RemoteGameSession] without real sockets.
 *
 * Buffers are generously sized: a [battletech.tactical.model.GameState]
 * snapshot serializes to a large JSON payload, and the default
 * [PipedInputStream] buffer (1 KiB) is nowhere near enough — writes would
 * block indefinitely against a reader that hasn't started draining yet.
 */
internal class PipedConnection {

    private val clientToServer: PipedOutputStream = PipedOutputStream()
    private val serverToClient: PipedOutputStream = PipedOutputStream()

    /** Server reads what the client writes. */
    val serverInput: BufferedReader = BufferedReader(InputStreamReader(PipedInputStream(clientToServer, PIPE_BUFFER_SIZE)))

    /** Server writes what the client reads. */
    val serverOutput: Writer = OutputStreamWriter(serverToClient)

    /** Client reads what the server writes. */
    val clientInput: BufferedReader = BufferedReader(InputStreamReader(PipedInputStream(serverToClient, PIPE_BUFFER_SIZE)))

    /** Client writes what the server reads. */
    val clientOutput: Writer = OutputStreamWriter(clientToServer)

    /**
     * Simulates the client hanging up: closes the client's WRITE end only,
     * which wakes the server's blocked `readLine()` with EOF. The read end is
     * left untouched on purpose — `BufferedReader.close()` contends for the
     * same lock a blocked `readLine()` holds, so closing a reader another
     * thread is reading from deadlocks.
     */
    fun closeClientSide() {
        clientOutput.close()
    }

    /**
     * Simulates the host/server disappearing: closes the server's WRITE end
     * only, which wakes the client's blocked `readLine()` with EOF (see
     * [closeClientSide] for why the read end must stay untouched).
     */
    fun closeServerSide() {
        serverOutput.close()
    }

    private companion object {
        private const val PIPE_BUFFER_SIZE = 1 shl 20
    }
}
