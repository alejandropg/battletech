package battletech.network.transport

import battletech.network.wire.ClientMessage
import battletech.network.wire.ServerMessage

/**
 * The server's end of a connection to one player, independent of what sits on the other end:
 * a real socket ([JsonLineConnection]) or an in-process peer ([InMemoryConnection]). This is
 * the seam [battletech.network.server.GameServer] will eventually hold one of PER SEAT instead
 * of the `BufferedReader`/`Writer` pair it wires up inline today — a hot-seat/host-local player
 * and a remote player both terminate here, and nothing on this interface lets the server tell
 * them apart.
 *
 * Threading contract mirrors the socket reality [JsonLineConnection] wraps: [receive] blocks
 * the calling thread until a message arrives or the peer is gone, so a caller reads on a
 * dedicated thread (as [battletech.network.server.GameServer]'s reader threads already do) and
 * writes ([send]) from whichever thread holds the message to deliver. Implementations are not
 * required to be safe for concurrent [send] calls from multiple threads unless they say so.
 */
internal interface ServerConnection {
    fun send(message: ServerMessage)

    /** Blocks for the next message. Returns null when the peer has closed / end of stream. */
    fun receive(): ClientMessage?

    fun close()
}

/** The client's end of a connection to the server — the mirror image of [ServerConnection]. */
internal interface ClientConnection {
    fun send(message: ClientMessage)

    /** Blocks for the next message. Returns null when the peer has closed / end of stream. */
    fun receive(): ServerMessage?

    fun close()
}
