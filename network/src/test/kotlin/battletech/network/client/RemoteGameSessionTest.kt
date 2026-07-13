package battletech.network.client

import battletech.network.PipedConnection
import battletech.network.aSampleSession
import battletech.network.advanceMovementUntilActivePlayerIs
import battletech.network.attachInBackground
import battletech.network.awaitTrue
import battletech.network.server.GameServer
import battletech.network.wire.ClientMessage
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.network.wire.WireJson
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.GameEvent
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.UnitMoved
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Drives [RemoteGameSession] over in-memory pipes against a real
 * [GameServer] (via its [GameServer.attachTransport] testability seam) — no
 * real sockets. Covers post-join state, local queries against the snapshot,
 * event dispatch, the push-before-reply ordering invariant from the client
 * side, and connection-loss handling.
 */
internal class RemoteGameSessionTest {

    private val sessionId = "TESTID"

    @Test
    fun `after join, gameState turnState and currentPhase reflect the post-kickstart snapshot`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        assertThat(remote.currentPhase).isEqualTo(server.currentPhase)
        assertThat(remote.gameState).isEqualTo(server.gameState)
        assertThat(remote.turnState).isEqualTo(server.turnState)
        assertThat(remote.activePlayer).isEqualTo(server.activePlayer)
        assertThat(remote.isMatchOver).isFalse()
    }

    @Test
    fun `playerId is the seat assigned by the server's JoinAccepted`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val remote = connectRemoteOverPipes(sessionId, connection)

        assertThat(remote.playerId).isEqualTo(PlayerId.PLAYER_2)
    }

    @Test
    fun `viewFor legalMovementsFor works against locally-held snapshot data`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        val unit = remote.gameState.unitsOf(PlayerId.PLAYER_2).first()
        val reachability = remote.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id)

        assertThat(reachability).isNotEmpty
        assertThat(reachability.first().destinations).isNotEmpty
    }

    @Test
    fun `a subscribed listener receives events pushed by the host`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        val events = mutableListOf<GameEvent>()
        remote.subscribe(PlayerId.PLAYER_2) { events += it }

        val active = server.turnState.movement.activePlayer
        val unit = server.turnState.selectableUnits(server.gameState).first()
        val reachability = server.viewFor(active).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val result = server.submitCommand(MoveUnit(active, unit.id, destination, reachability.mode))
        check(result is CommandResult.Accepted) { "setup move rejected: $result" }

        awaitTrue { events.any { it is UnitMoved } }
    }

    @Test
    fun `submitCommand returns the reply result and the snapshot is already fresh when it returns`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }
        server.advanceMovementUntilActivePlayerIs(PlayerId.PLAYER_2)
        awaitTrue { remote.turnState.movement.activePlayer == PlayerId.PLAYER_2 }

        val unit = remote.turnState.selectableUnits(remote.gameState).first()
        val reachability = remote.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val command = MoveUnit(PlayerId.PLAYER_2, unit.id, destination, reachability.mode)

        val result = remote.submitCommand(command)

        assertThat(result).isInstanceOf(CommandResult.Accepted::class.java)
        // The StatePush for this command is applied by the same reader thread before the
        // CommandReply that unblocks submitCommand — so the snapshot is already fresh here,
        // synchronously, with no further waiting.
        assertThat(remote.gameState.unitById(unit.id).position).isEqualTo(destination.position)
        assertThat(remote.turnState.movement.movedUnitIds).contains(unit.id)
    }

    @Test
    fun `connection lost appends a SessionNotice to the replica log and delivers it to subscribers, then submitCommand rejects with OpponentUnavailable`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }
        val events = mutableListOf<GameEvent>()
        remote.subscribe(PlayerId.PLAYER_2) { events += it }

        connection.closeServerSide()
        val expectedNotice = SessionNotice("Disconnected from host — restart with --join <host> --session <id> to rejoin")
        awaitTrue { events.contains(expectedNotice) }
        assertThat(remote.gameLog.snapshot().map { it.event }).contains(expectedNotice)

        val unit = remote.gameState.unitsOf(PlayerId.PLAYER_2).first()
        val reachability = remote.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val result = remote.submitCommand(MoveUnit(PlayerId.PLAYER_2, unit.id, destination, reachability.mode))

        assertThat(result).isEqualTo(CommandResult.Rejected(CommandRejection.OpponentUnavailable))
    }

    /** Mirrors [RemoteGameSession.connect] but over [PipedConnection] pipes instead of a real socket. */
    private fun connectRemoteOverPipes(sessionId: String, connection: PipedConnection): RemoteGameSession {
        connection.clientOutput.write(WireJson.encodeToLine(ClientMessage.Join(sessionId, PROTOCOL_VERSION)) + "\n")
        connection.clientOutput.flush()
        val line = connection.clientInput.readLine() ?: error("connection closed before join response")
        return when (val message = WireJson.decodeServerMessage(line)) {
            is ServerMessage.JoinAccepted -> RemoteGameSession(connection.clientInput, connection.clientOutput, message)
            is ServerMessage.JoinRejected -> throw JoinRejectedException(message.reason)
            else -> error("unexpected first message from host: $message")
        }
    }
}
