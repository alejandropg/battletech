package battletech.network.server

import battletech.network.transport.ServerConnection

/**
 * The one-method seam [SocketAcceptor] depends on instead of [GameServer] directly, so the same
 * acceptor can hand a freshly-accepted socket connection to either a committed match ([GameServer])
 * or a still-forming one ([LobbyHost]) — see `docs/architecture.md` for the shared-lobby rationale.
 * [GameServer]'s own public interface is untouched by this: it satisfies this seam through
 * [GameServer.asConnectionSink] (a small anonymous adapter), not by declaring `: ConnectionSink`
 * itself — [GameServer] is public and [ServerConnection] is deliberately not, so a direct
 * override would force this method's parameter public right along with it.
 */
internal interface ConnectionSink {
    /**
     * Performs whatever join handshake this sink speaks on [connection] and, on success, runs
     * that connection's reader loop inline (blocking the calling thread until disconnect).
     * [onJoinAccepted] fires once, right after a successful handshake — [SocketAcceptor] uses it
     * to clear the accept-side socket's handshake timeout.
     */
    fun attach(connection: ServerConnection, onJoinAccepted: () -> Unit = {})
}
