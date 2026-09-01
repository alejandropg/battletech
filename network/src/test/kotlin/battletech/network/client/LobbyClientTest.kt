package battletech.network.client

import battletech.network.awaitTrue
import battletech.network.server.GameServer
import battletech.network.server.LobbyHost
import battletech.network.server.SocketAcceptor
import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.content.AssetBundle
import battletech.tactical.model.content.ContentCatalog
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.session.CommandResult
import battletech.tactical.session.GameSession
import battletech.tactical.session.MoveUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Drives [LobbyClient] over a REAL TCP socket (port 0), mirroring [battletech.network.LocalhostEndToEndTest]'s
 * style. Covers both the parked lobby phase (catalog, selections, commit) and the already-committed
 * fast path, plus that [LobbyClient.awaitMatch]'s [ClientGameSession] behaves normally afterward —
 * the direct evidence that no second reader is left competing with it on the same connection (see
 * [LobbyClient]'s KDoc on why that would be a fatal bug).
 */
internal class LobbyClientTest {

    private var acceptor: SocketAcceptor? = null
    private var localSeat: GameSession? = null
    private var lobbyClient: LobbyClient? = null
    private var remote: ClientGameSession? = null

    @AfterEach
    fun tearDown() {
        acceptor?.close()
        remote?.close()
        lobbyClient?.close()
    }

    @Test
    fun `parking exposes the merged catalog, including the joiner's own content`() {
        val lobby = LobbyHost()
        val socketAcceptor = SocketAcceptor(lobby, port = 0)
        acceptor = socketAcceptor
        socketAcceptor.start()

        val extraMap = GameMap(hexes = mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0))), name = "joinerMap")
        val client = LobbyClient.connect("127.0.0.1", socketAcceptor.boundPort, lobby.sessionId, content = AssetBundle(maps = listOf(extraMap)))
        lobbyClient = client

        assertThat(client.catalog.maps).contains("joinerMap")
    }

    @Test
    fun `a plan the host publishes reaches onSelections`() {
        val lobby = LobbyHost()
        val socketAcceptor = SocketAcceptor(lobby, port = 0)
        acceptor = socketAcceptor
        socketAcceptor.start()

        val client = LobbyClient.connect("127.0.0.1", socketAcceptor.boundPort, lobby.sessionId)
        lobbyClient = client
        val received = ArrayBlockingQueue<MatchPlan>(1)
        client.onSelections { received.put(it) }

        val plan = MatchPlan(mapName = "battletech-classic")
        awaitTrue { lobby.opponentConnected } // don't publish before the peer is actually parked
        lobby.publish(plan)

        assertThat(received.poll(2, TimeUnit.SECONDS)).isEqualTo(plan)
    }

    @Test
    fun `commit fires onCommitted, and the resulting session plays normally with no duplicate reader`() {
        val lobby = LobbyHost()
        val socketAcceptor = SocketAcceptor(lobby, port = 0)
        acceptor = socketAcceptor
        socketAcceptor.start()

        val client = LobbyClient.connect("127.0.0.1", socketAcceptor.boundPort, lobby.sessionId)
        lobbyClient = client
        val committed = CountDownLatch(1)
        client.onCommitted { committed.countDown() }
        awaitTrue { lobby.opponentConnected }

        lateinit var local: ClientGameSession
        // connectLocal() runs inside onServerReady, BEFORE LobbyCommitted reaches the parked
        // peer — see LobbyHost.commit's KDoc for why "immediately after, same thread" alone
        // isn't a reliable enough guarantee against an already-blocked socket reader.
        val server = lobby.commit(ContentCatalog.load().resolveGame()) { local = it.connectLocal() }
        localSeat = local

        val joined = client.awaitMatch()
        remote = joined

        assertThat(committed.await(2, TimeUnit.SECONDS)).isTrue()
        assertThat(joined.playerId).isEqualTo(PlayerId.PLAYER_2)

        // If a second reader were still competing on this connection, the events below would be
        // split unpredictably between them and this convergence poll would time out rather than
        // ever catch up — each iteration polls BOTH sides to the server's turnState before
        // picking the next mover, mirroring LocalhostEndToEndTest's submitAndVerify.
        repeat(3) {
            awaitTrue { local.turnState == server.turnState && joined.turnState == server.turnState }
            val active = server.turnState.movement.activePlayer
            val actor = if (active == PlayerId.PLAYER_1) local else joined
            val unit = actor.turnState.selectableUnits(actor.stateFor(active).units).first()
            val reachability = actor.viewFor(active).legalMovementsFor(unit.id).first()
            val result = actor.submitCommand(MoveUnit(active, unit.id, reachability.destinations.first(), reachability.mode))
            assertThat(result).isInstanceOf(CommandResult.Accepted::class.java)
        }

        awaitTrue { joined.stateFor(joined.playerId) == server.stateFor(joined.playerId) }
        assertThat(joined.stateFor(joined.playerId)).isEqualTo(server.stateFor(joined.playerId))
    }

    @Test
    fun `joining an already-committed match returns immediately, with no lobby phase`() {
        val server = GameServer.host(ContentCatalog.load().resolveGame())
        val socketAcceptor = SocketAcceptor(server, port = 0)
        acceptor = socketAcceptor
        socketAcceptor.start()

        val client = LobbyClient.connect("127.0.0.1", socketAcceptor.boundPort, server.sessionId)
        lobbyClient = client

        val joined = client.awaitMatch()
        remote = joined

        assertThat(joined.playerId).isEqualTo(PlayerId.PLAYER_1)
    }
}
