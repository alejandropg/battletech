package battletech.network.server

import battletech.network.PipedConnection
import battletech.network.awaitTrue
import battletech.network.join
import battletech.network.sendJoin
import battletech.network.transport.JsonLineConnection
import battletech.network.wire.ClientMessage
import battletech.network.wire.JoinRejectionReason
import battletech.network.wire.ServerMessage
import battletech.network.wire.WireJson
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.content.AssetBundle
import battletech.tactical.model.content.ContentCatalog
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.session.CommandResult
import battletech.tactical.session.MoveUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

/** Drives [LobbyHost] over in-memory pipes ([PipedConnection]), exactly as [GameServerProtocolTest] drives [GameServer]. */
internal class LobbyHostTest {

    private val sessionId = "TESTID"

    private fun LobbyHost.attachInBackground(connection: PipedConnection): Thread =
        thread(isDaemon = true, name = "test-lobby-attach") {
            asConnectionSink().attach(JsonLineConnection.Server(connection.serverInput, connection.serverOutput))
        }

    private fun anInitialGameState(): GameState = ContentCatalog.load().resolveGame()

    @Test
    fun `a park sends LobbyJoined carrying the full merged registry, including the joiner's own content`() {
        val lobby = LobbyHost(sessionId)
        val connection = PipedConnection()
        lobby.attachInBackground(connection)

        val extraMap = GameMap(hexes = mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0))), name = "joinerMap")
        val response = connection.join(sessionId, content = AssetBundle(maps = listOf(extraMap)))

        val joined = response as ServerMessage.LobbyJoined
        assertThat(joined.registry.maps).containsEntry("joinerMap", extraMap)
        assertThat(lobby.registry.maps).containsKey("joinerMap")
        assertThat(lobby.opponentConnected).isTrue()
    }

    @Test
    fun `publish reaches the parked peer as LobbySelections`() {
        val lobby = LobbyHost(sessionId)
        val connection = PipedConnection()
        lobby.attachInBackground(connection)
        connection.join(sessionId)

        val plan = MatchPlan(mapName = "arena")
        lobby.publish(plan)

        val message = WireJson.decodeServerMessage(connection.clientInput.readLine())
        assertThat(message).isEqualTo(ServerMessage.LobbySelections(plan))
    }

    @Test
    fun `commit sends LobbyCommitted, and a re-sent Join reaches JoinAccepted so both seats can play`() {
        val lobby = LobbyHost(sessionId)
        val connection = PipedConnection()
        lobby.attachInBackground(connection)
        connection.join(sessionId)

        val server = lobby.commit(anInitialGameState())
        val localSeat = server.connectLocal()

        assertThat(WireJson.decodeServerMessage(connection.clientInput.readLine())).isEqualTo(ServerMessage.LobbyCommitted)

        connection.sendJoin(sessionId)
        val accepted = WireJson.decodeServerMessage(connection.clientInput.readLine()) as ServerMessage.JoinAccepted
        assertThat(accepted.bootstrap.playerId).isEqualTo(PlayerId.PLAYER_2)
        // Kickstart's StatePush follows the JoinAccepted for the seat that completes the roster.
        assertThat(WireJson.decodeServerMessage(connection.clientInput.readLine())).isInstanceOf(ServerMessage.StatePush::class.java)

        // The kickstart's StatePush to the LOCAL seat arrives asynchronously on its own reader
        // thread (see Main.kt's awaitKickstart KDoc for the same race) — poll rather than assert.
        awaitTrue { localSeat.currentPhase == TurnPhase.MOVEMENT }

        // Initiative is randomized (BattleSession's default, unseeded roller), so either seat
        // may move first — submit through whichever channel corresponds to the active player:
        // PLAYER_1 has a real ClientGameSession (localSeat), PLAYER_2 only a raw wire connection.
        val active = server.turnState.movement.activePlayer
        val unit = server.turnState.selectableUnits(server.stateFor(active).units).first()
        val reachability = server.viewFor(active).legalMovementsFor(unit.id).first()
        val command = MoveUnit(active, unit.id, reachability.destinations.first(), reachability.mode)

        if (active == PlayerId.PLAYER_1) {
            assertThat(localSeat.submitCommand(command)).isInstanceOf(CommandResult.Accepted::class.java)
        } else {
            connection.clientOutput.write(WireJson.encodeToLine(ClientMessage.SubmitCommand(requestId = 1L, command = command)) + "\n")
            connection.clientOutput.flush()
            // Wire ordering invariant (see ServerMessage's KDoc): the StatePush precedes the
            // CommandReply, and this connection is itself one of the clients that push fans out to.
            assertThat(WireJson.decodeServerMessage(connection.clientInput.readLine())).isInstanceOf(ServerMessage.StatePush::class.java)
            val reply = WireJson.decodeServerMessage(connection.clientInput.readLine()) as ServerMessage.CommandReply
            assertThat(reply.result).isInstanceOf(CommandResult.Accepted::class.java)
        }
    }

    @Test
    fun `a join arriving after commit is forwarded to the match instead of being parked`() {
        // An interactive host's acceptor is built over the lobby and never rebuilt over the
        // committed server, so every later connection still lands here. Parking one would strand
        // it waiting for a LobbyCommitted that has already been sent.
        val lobby = LobbyHost(sessionId)
        val server = lobby.commit(anInitialGameState())
        server.connectLocal()

        val late = PipedConnection()
        lobby.attachInBackground(late)
        late.sendJoin(sessionId)

        val reply = WireJson.decodeServerMessage(late.clientInput.readLine())
        assertThat(reply).isInstanceOf(ServerMessage.JoinAccepted::class.java)
        assertThat((reply as ServerMessage.JoinAccepted).bootstrap.playerId).isEqualTo(PlayerId.PLAYER_2)
    }

    @Test
    fun `the parked peer is un-parked at hand-off, so its seat can be rejoined later`() {
        val lobby = LobbyHost(sessionId)
        val parked = PipedConnection()
        lobby.attachInBackground(parked)
        parked.join(sessionId)

        val server = lobby.commit(anInitialGameState())
        server.connectLocal()
        assertThat(WireJson.decodeServerMessage(parked.clientInput.readLine())).isEqualTo(ServerMessage.LobbyCommitted)
        parked.sendJoin(sessionId)
        assertThat(WireJson.decodeServerMessage(parked.clientInput.readLine())).isInstanceOf(ServerMessage.JoinAccepted::class.java)

        // A stale `parked` would answer SEAT_TAKEN here from the LOBBY, before the committed
        // server ever got to decide. The seat really is taken, so SEAT_TAKEN is still the right
        // answer — but it must come from GameServer, which is what makes a genuine rejoin (after
        // that seat disconnects) possible at all.
        awaitTrue { !lobby.opponentConnected }
    }

    @Test
    fun `a drop while parked reports opponentConnected false, not another arrival`() {
        val lobby = LobbyHost(sessionId)
        val connection = PipedConnection()
        lobby.attachInBackground(connection)
        connection.join(sessionId)

        val statuses = mutableListOf<LobbyStatus>()
        lobby.onChange { statuses += it }
        connection.clientOutput.close()

        awaitTrue { statuses.any { !it.opponentConnected } }
    }

    @Test
    fun `a second joiner is rejected SEAT_TAKEN`() {
        val lobby = LobbyHost(sessionId)
        val first = PipedConnection()
        lobby.attachInBackground(first)
        first.join(sessionId)

        val second = PipedConnection()
        lobby.attachInBackground(second)
        val rejection = second.join(sessionId) as ServerMessage.JoinRejected
        assertThat(rejection.reason).isEqualTo(JoinRejectionReason.SEAT_TAKEN)
    }

    @Test
    fun `a bad bundle is rejected INVALID_CONTENT`() {
        val lobby = LobbyHost(sessionId)
        val connection = PipedConnection()
        lobby.attachInBackground(connection)

        val dupeMap = GameMap(hexes = emptyMap(), name = "dupe")
        val bundle = AssetBundle(maps = listOf(dupeMap, dupeMap.copy(hexes = mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0))))))

        val rejection = connection.join(sessionId, content = bundle) as ServerMessage.JoinRejected
        assertThat(rejection.reason).isEqualTo(JoinRejectionReason.INVALID_CONTENT)
    }

    @Test
    fun `a peer that drops while parked un-parks and fires onChange`() {
        val lobby = LobbyHost(sessionId)
        val connection = PipedConnection()
        lobby.attachInBackground(connection)
        connection.join(sessionId)
        assertThat(lobby.opponentConnected).isTrue()

        val changes = mutableListOf<Boolean>()
        lobby.onChange { changes += lobby.opponentConnected }

        connection.closeClientSide()

        awaitTrue { !lobby.opponentConnected }
        assertThat(changes).contains(false)
    }

    @Test
    fun `hot-seat shape -- no acceptor, commit, two connectLocal, kickstart fires once`() {
        val lobby = LobbyHost()

        val server = lobby.commit(anInitialGameState())
        val seats = List(PlayerId.entries.size) { server.connectLocal() }.associateBy { it.playerId }

        assertThat(seats.keys).isEqualTo(PlayerId.entries.toSet())
        // Mirrors Main.kt's awaitKickstart: a connectLocal() call can return before its own
        // session's reader thread has applied the kickstart's StatePush.
        awaitTrue { seats.values.all { it.currentPhase == server.currentPhase } }
        assertThat(server.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
    }
}
