package battletech.network.transport

import battletech.network.awaitTrue
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

/** Drives [InMemoryConnection.pair] directly: object identity through the queues, ordering, and the close/poison mechanism. */
internal class InMemoryConnectionTest {

    private val aClientMessage = ClientMessage.SubmitCommand(1L, StandUp(PlayerId.PLAYER_1, UnitId("unit-1")))
    private val aServerMessage = ServerMessage.CommandReply(1L, CommandResult.Rejected(CommandRejection.OpponentUnavailable))

    @Test
    fun `a message sent from the server side is received on the client side, and vice versa`() {
        val (server, client) = InMemoryConnection.pair()

        server.send(aServerMessage)
        client.send(aClientMessage)

        assertThat(client.receive()).isSameAs(aServerMessage)
        assertThat(server.receive()).isSameAs(aClientMessage)
    }

    @Test
    fun `ordering is preserved under multiple sends`() {
        val (server, client) = InMemoryConnection.pair()
        val messages = (1..5).map { ServerMessage.CommandReply(it.toLong(), CommandResult.Rejected(CommandRejection.OpponentUnavailable)) }

        messages.forEach { server.send(it) }

        val received = (1..5).map { client.receive() }
        assertThat(received).containsExactlyElementsOf(messages)
    }

    @Test
    fun `closing the server side unblocks a client receive() blocked waiting, returning null`() {
        val (server, client) = InMemoryConnection.pair()
        val result = AtomicReference<ServerMessage?>(null)

        val reader = thread(isDaemon = true) { result.set(client.receive()) }
        awaitTrue { reader.state == Thread.State.WAITING || reader.state == Thread.State.TIMED_WAITING }

        server.close()
        reader.join(2_000)

        assertThat(reader.isAlive).isFalse()
        assertThat(result.get()).isNull()
    }

    @Test
    fun `closing the client side unblocks a server receive() blocked waiting, returning null`() {
        val (server, client) = InMemoryConnection.pair()
        val result = AtomicReference<ClientMessage?>(null)

        val reader = thread(isDaemon = true) { result.set(server.receive()) }
        awaitTrue { reader.state == Thread.State.WAITING || reader.state == Thread.State.TIMED_WAITING }

        client.close()
        reader.join(2_000)

        assertThat(reader.isAlive).isFalse()
        assertThat(result.get()).isNull()
    }

    @Test
    fun `closing the server side ALSO unblocks the server's own receive() blocked waiting, returning null`() {
        // Discriminates the actual fix: the earlier implementation only poisoned the
        // PEER's queue on close(), leaving a connection's own blocked receive() parked
        // forever after it closed itself. Daemon thread + bounded join: if this
        // regresses, the test fails fast (isAlive stays true) rather than hanging.
        val (server, client) = InMemoryConnection.pair()
        val result = AtomicReference<ClientMessage?>(null)

        val reader = thread(isDaemon = true) { result.set(server.receive()) }
        awaitTrue { reader.state == Thread.State.WAITING || reader.state == Thread.State.TIMED_WAITING }

        server.close()
        reader.join(2_000)

        assertThat(reader.isAlive).isFalse()
        assertThat(result.get()).isNull()
    }

    @Test
    fun `closing the client side ALSO unblocks the client's own receive() blocked waiting, returning null`() {
        val (server, client) = InMemoryConnection.pair()
        val result = AtomicReference<ServerMessage?>(null)

        val reader = thread(isDaemon = true) { result.set(client.receive()) }
        awaitTrue { reader.state == Thread.State.WAITING || reader.state == Thread.State.TIMED_WAITING }

        client.close()
        reader.join(2_000)

        assertThat(reader.isAlive).isFalse()
        assertThat(result.get()).isNull()
    }

    @Test
    fun `close is idempotent and repeated receive() calls after close keep returning null rather than blocking`() {
        val (server, client) = InMemoryConnection.pair()

        server.close()
        server.close()

        assertThat(client.receive()).isNull()
        assertThat(client.receive()).isNull()
        assertThat(client.receive()).isNull()
    }

    @Test
    fun `a message already queued before close is still delivered before EOF`() {
        val (server, client) = InMemoryConnection.pair()

        server.send(aServerMessage)
        server.close()

        assertThat(client.receive()).isSameAs(aServerMessage)
        assertThat(client.receive()).isNull()
    }
}
