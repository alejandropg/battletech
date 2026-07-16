package battletech.network.transport

import battletech.network.wire.ClientMessage
import battletech.network.wire.ServerMessage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Factory for an in-process [ServerConnection]/[ClientConnection] pair that swaps message
 * OBJECTS directly through two [LinkedBlockingQueue]s — no serialization, no bytes, no socket.
 * This is the local-player adapter the [ServerConnection]/[ClientConnection] port exists for:
 * it presents the exact same surface [JsonLineConnection] presents over a real socket, so a
 * caller holding one of these — [battletech.network.server.GameServer], once wired up — cannot
 * tell a hot-seat/host-local player from a remote one.
 *
 * ### Close / poison mechanism
 *
 * `close()` means "this connection is finished" — both directions die, matching real socket
 * semantics: [battletech.network.server.GameServer] disconnects a client today by closing the
 * underlying `Socket`, which unblocks BOTH that client's writer (a subsequent `write` fails) AND
 * the server's own reader thread parked in `input.readLine()` (it throws `SocketException`). A
 * [ServerConnection]/[ClientConnection] that only unblocked the PEER's `receive()` and left its
 * own blocked forever would be a real behavioral difference a caller (eventually `GameServer`)
 * could use to tell this adapter apart from [JsonLineConnection] — exactly what the port exists
 * to prevent.
 *
 * There are two [PoisonableQueue]s, one per direction: `toClient` (written by [pair]'s server
 * half via [ServerConnection.send], read by the client half's [ClientConnection.receive]) and
 * `toServer` (the reverse). `close()` on either half enqueues the private [Closed] sentinel onto
 * BOTH queues — `writeTo` (so the peer's blocked `receive()` sees EOF) and `readFrom` (so this
 * half's OWN blocked `receive()` sees EOF too).
 *
 * [PoisonableQueue.receive] never re-blocks once it has seen [Closed]: taking [Closed] off the
 * underlying queue sets a `sawClosed` flag and returns null WITHOUT putting [Closed] back, and
 * every later `receive` call checks that flag before ever touching the queue again. This (not
 * "requeue the sentinel", which would let a message enqueued after `close()` leapfrog back in
 * front of it) is what makes the following true:
 *  - **A message enqueued before `close()` is still delivered.** If the peer sent `M1` before I
 *    closed, my queue is `[M1, Closed]` at the moment of closing; `receive()` drains `M1` first
 *    (FIFO, `sawClosed` still false) and only sees `Closed` — and flips `sawClosed` — on the next
 *    call. Nothing already in flight is discarded.
 *  - **A message enqueued after `close()` is never delivered.** If the peer sends `M2` after I
 *    closed, my queue is `[Closed, M2]`; the first `receive()` call takes `Closed` (FIFO head),
 *    sets `sawClosed`, and returns null. Every later call short-circuits on the flag and never
 *    calls `take()` again, so `M2` — sitting behind the sentinel — is never dequeued. That's
 *    correct: I closed, so nothing sent afterward is mine to receive.
 *  - **Can't deadlock.** The thread parked in `receive`'s `queue.take()` is woken by the very
 *    `put(Closed)` that `close()` performs on that same queue — no separate signal, nothing else
 *    to coordinate.
 *  - **Repeated `receive()` calls after EOF never re-block.** The `sawClosed` flag, not the
 *    queue's contents, is what `receive` consults first, so "closed" is sticky per half rather
 *    than being a one-shot token that could be exhausted.
 *
 * `close()` itself is idempotent (guarded by each half's own [AtomicBoolean]), so calling it
 * more than once on the same half enqueues [Closed] at most once per queue from that half. Both
 * halves closing independently can still each enqueue their own [Closed] onto a given queue —
 * one half poisons it as `writeTo`, the other as `readFrom` — so a queue may carry up to two
 * [Closed] tokens. That's harmless: the reader only ever consumes the first one it reaches
 * (`sawClosed` short-circuits every call after), so the second sits unread rather than causing
 * unbounded growth or being misinterpreted as a real message.
 */
internal object InMemoryConnection {

    /** Enqueued by [PoisonableQueue.poison] to unblock a blocked [PoisonableQueue.receive]; see class KDoc. */
    private object Closed

    internal fun pair(): Pair<ServerConnection, ClientConnection> {
        val toClient = PoisonableQueue()
        val toServer = PoisonableQueue()
        return ServerHalf(readFrom = toServer, writeTo = toClient) to ClientHalf(readFrom = toClient, writeTo = toServer)
    }

    /** One direction's queue, plus the "have I already hit EOF" latch [receive] needs to stay closed once poisoned. See class KDoc. */
    private class PoisonableQueue {
        private val queue: LinkedBlockingQueue<Any> = LinkedBlockingQueue()
        private val sawClosed: AtomicBoolean = AtomicBoolean(false)

        fun send(message: Any) {
            queue.put(message)
        }

        fun poison() {
            queue.put(Closed)
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> receive(): T? {
            if (sawClosed.get()) return null
            val item = queue.take()
            if (item === Closed) {
                sawClosed.set(true)
                return null
            }
            return item as T
        }
    }

    private class ServerHalf(
        private val readFrom: PoisonableQueue,
        private val writeTo: PoisonableQueue,
    ) : ServerConnection {
        private val closed = AtomicBoolean(false)

        override fun send(message: ServerMessage) {
            writeTo.send(message)
        }

        override fun receive(): ClientMessage? = readFrom.receive()

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                writeTo.poison()
                readFrom.poison()
            }
        }
    }

    private class ClientHalf(
        private val readFrom: PoisonableQueue,
        private val writeTo: PoisonableQueue,
    ) : ClientConnection {
        private val closed = AtomicBoolean(false)

        override fun send(message: ClientMessage) {
            writeTo.send(message)
        }

        override fun receive(): ServerMessage? = readFrom.receive()

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                writeTo.poison()
                readFrom.poison()
            }
        }
    }
}
