package battletech.network.transport

import battletech.network.wire.ClientMessage
import battletech.network.wire.ServerMessage
import battletech.network.wire.WireJson
import java.io.BufferedReader
import java.io.IOException
import java.io.Writer

/**
 * [ServerConnection]/[ClientConnection] adapters over a `BufferedReader`/`Writer` pair, speaking
 * the same newline-delimited JSON [battletech.network.server.GameServer] and
 * [battletech.network.client.ClientGameSession] already exchange over a socket — this relocates
 * that behavior behind the port unchanged rather than altering it. [WireJson] owns the actual
 * encoding; this class only owns the line framing.
 *
 * Every `send` writes one line (`WireJson.encodeToLine(message) + "\n"`) and flushes immediately
 * — a socket's `Writer` buffers internally, and nothing else in this class drives a flush, so a
 * caller expecting the peer to see a message promptly would otherwise stall on it. Every
 * `receive` is one `readLine()`.
 *
 * `receive` catches [IOException] from the underlying read and returns null, the same as a clean
 * EOF — this is the port's contract ([ServerConnection.receive]/[ClientConnection.receive]:
 * "null = peer closed / end of stream"), and collapsing "the socket died mid-read" into the same
 * null a graceful close produces is what lets [InMemoryConnection] and this class present
 * IDENTICAL behavior to a caller: neither today's call sites nor the port's contract distinguish
 * the two. `GameServer.runReaderLoop` and `ClientGameSession.readLoop` already `catch (e:
 * IOException)` around their raw `readLine()` loops and treat it exactly like a `null`/break —
 * neither branches on it — so this is not a behavior change, only moving that catch below the
 * port instead of leaving it to every caller to remember. A decode failure (malformed JSON) is
 * NOT an [IOException] — [WireJson] throws `kotlinx.serialization.SerializationException`, which
 * extends `IllegalArgumentException` — so it still propagates out of `receive` uncaught, same as
 * today. A non-matching message TYPE (e.g. the server reads a `ClientMessage.Join` where a
 * `SubmitCommand` was expected) is likewise left to the caller to filter, same as today's
 * `as? ClientMessage.SubmitCommand ?: continue`.
 *
 * ### `close()`'s order is load-bearing
 *
 * `close()` closes `output` BEFORE `input` — reversed from the naive order — because `input` is
 * a [BufferedReader], and [BufferedReader.readLine]/[BufferedReader.close] both synchronize on
 * the reader's own lock for their ENTIRE call, including however long the underlying blocking
 * read takes. If some other thread is parked in `input.readLine()` (this connection's own reader
 * thread, blocked waiting for the peer's next message) when `close()` runs on a different thread,
 * calling `input.close()` FIRST would itself block trying to acquire that same lock — and it
 * would stay blocked for as long as the read does, i.e. forever, since nothing is going to make
 * that read return. That's a real deadlock, not a hang bounded by anything: confirmed empirically
 * (piped and real-socket) before this ordering fix went in.
 *
 * `output` has no such lock-sharing problem, and — over a real `Socket` — closing the `OutputStream`
 * closes the whole socket (per `Socket.getOutputStream`'s contract), both directions. So closing
 * `output` first triggers the underlying socket's actual close at the OS/fd level, which makes the
 * blocked native read inside `input.readLine()` on the OTHER thread throw `SocketException`
 * immediately — WITHOUT ever needing to acquire `input`'s lock. By the time this call reaches
 * `input.close()`, nothing is blocked in `readLine()` on it anymore, so that second close is fast
 * and safe. This is exactly why [battletech.network.server.GameServer]'s disconnect path today
 * closes the `Socket` object itself (`onDisconnect = { socket.close() }`) rather than the
 * `BufferedReader`/`Writer` pair directly — same underlying reason, expressed at the socket layer
 * instead of here.
 *
 * One real gap remains, inherent to the TEST harness rather than to this class: over
 * [battletech.network.PipedConnection], `input` and `output` are two INDEPENDENT pipes (not two
 * views of one shared fd, unlike a real socket), so closing `output` there has no effect on a
 * thread blocked reading `input` — see [battletech.network.PipedConnection]'s own KDoc, which
 * documents the same asymmetry for exactly this reason. This class's "close unblocks my OWN
 * blocked receive()" behavior is therefore proven against a real `Socket` pair, not against
 * pipes; `InMemoryConnection`'s equivalent proof runs directly against its own queues, since it
 * has no such split-stream artifact to work around. (What `receive` reports when this happens —
 * null, per the `IOException`-catching contract above — is identical either way; only the TEST
 * harness's ability to exercise "close while my own receive is blocked" differs, not the
 * production behavior.)
 */
internal object JsonLineConnection {

    /** The server-side form: sends [ServerMessage]s, receives [ClientMessage]s. */
    internal class Server(private val input: BufferedReader, private val output: Writer) : ServerConnection {
        override fun send(message: ServerMessage) {
            output.write(WireJson.encodeToLine(message) + "\n")
            output.flush()
        }

        override fun receive(): ClientMessage? {
            val line = try {
                input.readLine() ?: return null
            } catch (e: IOException) {
                return null
            }
            return WireJson.decodeClientMessage(line)
        }

        override fun close() {
            output.close()
            input.close()
        }
    }

    /** The client-side form: sends [ClientMessage]s, receives [ServerMessage]s. */
    internal class Client(private val input: BufferedReader, private val output: Writer) : ClientConnection {
        override fun send(message: ClientMessage) {
            output.write(WireJson.encodeToLine(message) + "\n")
            output.flush()
        }

        override fun receive(): ServerMessage? {
            val line = try {
                input.readLine() ?: return null
            } catch (e: IOException) {
                return null
            }
            return WireJson.decodeServerMessage(line)
        }

        override fun close() {
            output.close()
            input.close()
        }
    }
}
