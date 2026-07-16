package battletech.network.transport

import battletech.network.PipedConnection
import battletech.network.awaitTrue
import battletech.network.wire.ClientMessage
import battletech.network.wire.ServerMessage
import battletech.tactical.model.PlayerId
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.StandUp
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Stream
import kotlin.concurrent.thread

/**
 * Asserts [InMemoryConnection] and [JsonLineConnection] satisfy IDENTICAL send/receive/close
 * semantics through the [ServerConnection]/[ClientConnection] port. That symmetry is the whole
 * design claim these adapters exist to make -- a caller holding the port (eventually
 * [battletech.network.server.GameServer], per seat) cannot tell a local pair from a wire pair
 * apart -- so it is asserted directly here, once per behavior.
 *
 * Two parameter sources, not one: [pairs] backs every test that doesn't touch close (round trip,
 * ordering) and the PEER-unblock close tests, using [battletech.network.PipedConnection] for the
 * JsonLine side since those properties hold identically over pipes and a real socket. The
 * OWN-close tests use [pairsForOwnClose] instead, which opens a real loopback socket for the
 * JsonLine side -- see [realJsonLineSocketPair]'s KDoc for why pipes can't exercise that one
 * property (its two directions are independent streams, not two views of one shared fd).
 */
internal class ConnectionPortSymmetryTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairs")
    fun `a message sent from the server side is received on the client side, and vice versa`(
        @Suppress("UNUSED_PARAMETER") label: String,
        factory: () -> Pair<ServerConnection, ClientConnection>,
    ) {
        val (server, client) = factory()

        server.send(aServerMessage)
        client.send(aClientMessage)

        assertThat(client.receive()).isEqualTo(aServerMessage)
        assertThat(server.receive()).isEqualTo(aClientMessage)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairs")
    fun `ordering is preserved under multiple sends`(
        @Suppress("UNUSED_PARAMETER") label: String,
        factory: () -> Pair<ServerConnection, ClientConnection>,
    ) {
        val (server, client) = factory()
        val replies = (1..5).map { ServerMessage.CommandReply(it.toLong(), CommandResult.Rejected(CommandRejection.OpponentUnavailable)) }

        replies.forEach { server.send(it) }

        assertThat((1..5).map { client.receive() }).containsExactlyElementsOf(replies)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairs")
    fun `closing the server side unblocks a client receive() blocked waiting, returning null`(
        @Suppress("UNUSED_PARAMETER") label: String,
        factory: () -> Pair<ServerConnection, ClientConnection>,
    ) {
        val (server, client) = factory()
        val result = AtomicReference<ServerMessage?>(null)

        val reader = thread(isDaemon = true) { result.set(client.receive()) }
        awaitTrue { reader.state == Thread.State.WAITING || reader.state == Thread.State.TIMED_WAITING }

        server.close()
        reader.join(2_000)

        assertThat(reader.isAlive).isFalse()
        assertThat(result.get()).isNull()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairs")
    fun `closing the client side unblocks a server receive() blocked waiting, returning null`(
        @Suppress("UNUSED_PARAMETER") label: String,
        factory: () -> Pair<ServerConnection, ClientConnection>,
    ) {
        val (server, client) = factory()
        val result = AtomicReference<ClientMessage?>(null)

        val reader = thread(isDaemon = true) { result.set(server.receive()) }
        awaitTrue { reader.state == Thread.State.WAITING || reader.state == Thread.State.TIMED_WAITING }

        client.close()
        reader.join(2_000)

        assertThat(reader.isAlive).isFalse()
        assertThat(result.get()).isNull()
    }

    // ---------- own-close: was the property that discriminated the two adapters; now identical ----------

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairsForOwnClose")
    fun `closing the server side ALSO unblocks the server's own receive() blocked waiting, returning null`(
        @Suppress("UNUSED_PARAMETER") label: String,
        factory: () -> ConnectionsUnderTest,
    ) {
        val (server, _, cleanup) = factory()
        cleanup.use {
            val result = AtomicReference<ClientMessage?>(null)
            val reader = thread(isDaemon = true) { result.set(server.receive()) }
            // A blocked native socket read reports as RUNNABLE, not WAITING like the in-memory
            // adapter's queue.take() -- a flat, generous sleep (rather than state-polling) is
            // the one wait strategy that works unmodified for both parameterizations.
            Thread.sleep(200)

            server.close()
            reader.join(3_000)

            assertThat(reader.isAlive).isFalse()
            assertThat(result.get()).isNull()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairsForOwnClose")
    fun `closing the client side ALSO unblocks the client's own receive() blocked waiting, returning null`(
        @Suppress("UNUSED_PARAMETER") label: String,
        factory: () -> ConnectionsUnderTest,
    ) {
        val (_, client, cleanup) = factory()
        cleanup.use {
            val result = AtomicReference<ServerMessage?>(null)
            val reader = thread(isDaemon = true) { result.set(client.receive()) }
            Thread.sleep(200)

            client.close()
            reader.join(3_000)

            assertThat(reader.isAlive).isFalse()
            assertThat(result.get()).isNull()
        }
    }

    /** [server]/[client] under test, plus whatever [cleanup] the pair needs once the test is done (a no-op for in-memory). */
    internal data class ConnectionsUnderTest(
        val server: ServerConnection,
        val client: ClientConnection,
        val cleanup: AutoCloseable = AutoCloseable {},
    )

    private companion object {
        private val aClientMessage = ClientMessage.SubmitCommand(1L, StandUp(PlayerId.PLAYER_1, UnitId("unit-1")))
        private val aServerMessage = ServerMessage.CommandReply(1L, CommandResult.Rejected(CommandRejection.OpponentUnavailable))

        @JvmStatic
        fun pairs(): Stream<Arguments> = Stream.of(
            Arguments.of("InMemoryConnection", { InMemoryConnection.pair() }),
            Arguments.of("JsonLineConnection", { jsonLinePair() }),
        )

        @JvmStatic
        fun pairsForOwnClose(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "InMemoryConnection",
                {
                    val (server, client) = InMemoryConnection.pair()
                    ConnectionsUnderTest(server, client)
                },
            ),
            Arguments.of(
                "JsonLineConnection",
                {
                    val (connections, cleanup) = realJsonLineSocketPair()
                    ConnectionsUnderTest(connections.first, connections.second, cleanup)
                },
            ),
        )

        private fun jsonLinePair(): Pair<ServerConnection, ClientConnection> {
            val pipes = PipedConnection()
            return JsonLineConnection.Server(pipes.serverInput, pipes.serverOutput) to
                JsonLineConnection.Client(pipes.clientInput, pipes.clientOutput)
        }
    }
}
