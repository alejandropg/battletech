package battletech.network.server

import battletech.network.transport.JsonLineConnection
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Owns the listening TCP socket for a [GameServer]: binds [port] eagerly in the constructor (so
 * [boundPort] is meaningful immediately, even before [start] runs the accept loop — mirroring how
 * [GameServer] itself used to bind before this class existed), accepts connections on a daemon
 * thread, and hands each one to [GameServer.attach] as a [JsonLineConnection.Server].
 *
 * Split out of [GameServer] because a [GameServer] must be constructible with NO socket at all —
 * hot-seat play needs the single command-authority + redaction seam [GameServer] provides, but
 * must never bind a port for a solitaire game where both seats are
 * [GameServer.connectLocal] clients. Every socket-specific concern — the [ServerSocket], the
 * accept loop, the per-connection handshake timeout — lives here now; [GameServer] itself knows
 * nothing about TCP, sockets, or ports.
 *
 * A `--host` launch that wants BOTH a local seat and a listening port constructs both: call
 * [GameServer.connectLocal] before calling [start] here (see [GameServer]'s KDoc for why that
 * order is what guarantees the local player gets `PLAYER_1`), then [start] to open the door for
 * the remote seat. A `--server` (headless) launch skips [GameServer.connectLocal] entirely and
 * only ever uses this class. Hot-seat uses neither.
 */
public class SocketAcceptor(
    private val server: GameServer,
    port: Int,
) : AutoCloseable {

    private val serverSocket: ServerSocket = ServerSocket(port)

    @Volatile
    private var running: Boolean = true

    /** The actual bound port — meaningful even when constructed with port 0. */
    public val boundPort: Int get() = serverSocket.localPort

    /** Starts the accept loop on a daemon thread. Safe to call once. */
    public fun start() {
        thread(isDaemon = true, name = "game-server-accept") {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    null
                }
                when {
                    socket != null -> handleClientSocket(socket)
                    !running -> return@thread
                }
            }
        }
    }

    /** Stops the accept loop and closes the listening socket. Does not touch already-attached clients — that's [GameServer.close]'s job. */
    public override fun close() {
        running = false
        serverSocket.close()
    }

    private fun handleClientSocket(socket: Socket) {
        thread(isDaemon = true, name = "game-server-client") {
            try {
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = OutputStreamWriter(socket.getOutputStream())
                val connection = JsonLineConnection.Server(input, output)
                server.attach(connection, onJoinAccepted = { socket.soTimeout = 0 })
            } catch (e: IOException) {
                socket.close()
            }
        }
    }

    private companion object {
        private const val HANDSHAKE_TIMEOUT_MS = 5000
    }
}
