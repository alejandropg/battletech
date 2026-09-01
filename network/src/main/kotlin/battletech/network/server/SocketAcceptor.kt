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
 * Owns the listening TCP socket for whatever [ConnectionSink] it is constructed over — a
 * committed [GameServer] or a still-forming [LobbyHost]: binds [port] eagerly in the constructor
 * (so [boundPort] is meaningful immediately, even before [start] runs the accept loop), accepts
 * connections on a daemon thread, and hands each one to [ConnectionSink.attach] as a
 * [JsonLineConnection.Server].
 *
 * Every socket-specific concern — the [ServerSocket], the accept loop, the per-connection
 * handshake timeout — lives here, so neither [GameServer] nor [LobbyHost] knows anything about
 * TCP, sockets, or ports.
 *
 * A launch that wants BOTH a local seat and a listening port constructs both, and must call
 * [GameServer.connectLocal] before calling [start] here — see [GameServer]'s KDoc for why that
 * order is what guarantees the local player gets `PLAYER_1`.
 */
public class SocketAcceptor internal constructor(
    private val sink: ConnectionSink,
    port: Int,
) : AutoCloseable {

    /** Listens for a committed match. */
    public constructor(server: GameServer, port: Int) : this(server.asConnectionSink(), port)

    /** Listens for a still-forming lobby — see [LobbyHost]. */
    public constructor(lobby: LobbyHost, port: Int) : this(lobby.asConnectionSink(), port)

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
                sink.attach(connection, onJoinAccepted = { socket.soTimeout = 0 })
            } catch (e: IOException) {
                socket.close()
            }
        }
    }

    private companion object {
        private const val HANDSHAKE_TIMEOUT_MS = 5000
    }
}
