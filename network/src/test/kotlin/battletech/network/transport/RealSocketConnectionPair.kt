package battletech.network.transport

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Opens a real loopback TCP connection and wraps both ends as [JsonLineConnection.Server]/
 * [JsonLineConnection.Client]. Tests that need [JsonLineConnection]'s real-socket close semantics
 * — closing `output` closes the whole socket, unlike [battletech.network.PipedConnection]'s two
 * independent pipes — use this instead of pipes; see [JsonLineConnection]'s class KDoc.
 *
 * `accept()` runs on a bounded-join daemon thread rather than the calling thread, since a
 * `ServerSocket.accept()` that never completes would otherwise hang the caller with no way out;
 * [check] fails fast instead if that 3s bound is ever hit.
 *
 * The returned [AutoCloseable] closes all three sockets (`serverSocket`, the accepted socket, and
 * the client socket) — callers should close it once done, typically in a `try`/`finally` or
 * `.use { }`, even though each socket is also independently closed by whichever
 * [ServerConnection.close]/[ClientConnection.close] a given test exercises.
 */
internal fun realJsonLineSocketPair(): Pair<Pair<ServerConnection, ClientConnection>, AutoCloseable> {
    val serverSocket = ServerSocket(0)
    lateinit var accepted: Socket
    val acceptThread = thread(isDaemon = true, name = "test-accept") { accepted = serverSocket.accept() }
    val clientSocket = Socket("localhost", serverSocket.localPort)
    acceptThread.join(3_000)
    check(!acceptThread.isAlive) { "ServerSocket.accept() never completed within the bounded wait" }

    val server: ServerConnection = JsonLineConnection.Server(
        BufferedReader(InputStreamReader(accepted.getInputStream())),
        OutputStreamWriter(accepted.getOutputStream()),
    )
    val client: ClientConnection = JsonLineConnection.Client(
        BufferedReader(InputStreamReader(clientSocket.getInputStream())),
        OutputStreamWriter(clientSocket.getOutputStream()),
    )
    val cleanup = AutoCloseable {
        accepted.close()
        clientSocket.close()
        serverSocket.close()
    }
    return (server to client) to cleanup
}
