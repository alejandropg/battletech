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
import battletech.tactical.query.ForeignUnit
import battletech.tactical.query.OwnUnit
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.GameEvent
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.UnitMoved
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Drives [RemoteGameSession] over in-memory pipes against a real
 * [GameServer] (via its [GameServer.attachTransport] testability seam) — no
 * real sockets. Covers post-join state, local queries against the snapshot,
 * event dispatch, the push-before-reply ordering invariant from the client
 * side, connection-loss handling, and — the point of this stage — that the
 * snapshot/log this replica holds is already redacted for [RemoteGameSession.playerId],
 * never PLAYER_1's private fields.
 */
internal class RemoteGameSessionTest {

    private val sessionId = "TESTID"

    @Test
    fun `after join, stateFor turnState and currentPhase reflect the post-kickstart snapshot`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        assertThat(remote.currentPhase).isEqualTo(server.currentPhase)
        assertThat(remote.stateFor(remote.playerId)).isEqualTo(server.stateFor(PlayerId.PLAYER_2))
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

    // ---------- the payoff: what this client actually holds is already redacted ----------

    @Test
    fun `stateFor(playerId) shows PLAYER_1's units as ForeignUnit and this seat's own as OwnUnit`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        val units = remote.stateFor(remote.playerId).units
        assertThat(units).isNotEmpty
        units.filter { it.owner == PlayerId.PLAYER_1 }.forEach { assertThat(it).isInstanceOf(ForeignUnit::class.java) }
        units.filter { it.owner == PlayerId.PLAYER_2 }.forEach { assertThat(it).isInstanceOf(OwnUnit::class.java) }
    }

    @Test
    fun `stateFor and logFor refuse a viewer other than this replica's own seat`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        assertThatThrownBy { remote.stateFor(PlayerId.PLAYER_1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { remote.stateFor(null) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { remote.logFor(PlayerId.PLAYER_1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { remote.logFor(null) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `viewFor is not supported on a remote replica — no raw GameState to compute from`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        assertThatThrownBy { remote.viewFor(remote.playerId) }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `a subscribed listener receives events pushed by the host`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        val events = mutableListOf<GameEvent>()
        remote.subscribe { events += it }

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

        // The replica still knows its OWN units (stateFor(remote.playerId) — OwnUnit, full
        // fidelity id/position); the reachability MAP itself has to come from the host's
        // viewFor, since RemoteGameSession.viewFor no longer has raw state to compute one
        // (see that method's KDoc) — a known, reported limitation of this stage.
        val unit = remote.turnState.selectableUnits(remote.stateFor(remote.playerId)).first()
        val reachability = server.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val command = MoveUnit(PlayerId.PLAYER_2, unit.id, destination, reachability.mode)

        val result = remote.submitCommand(command)

        assertThat(result).isInstanceOf(CommandResult.Accepted::class.java)
        // The StatePush for this command is applied by the same reader thread before the
        // CommandReply that unblocks submitCommand — so the snapshot is already fresh here,
        // synchronously, with no further waiting.
        assertThat(remote.stateFor(remote.playerId).unitById(unit.id).position).isEqualTo(destination.position)
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
        remote.subscribe { events += it }

        connection.closeServerSide()
        val expectedNotice = SessionNotice("Disconnected from host — restart with --join <host> --session <id> to rejoin")
        awaitTrue { events.contains(expectedNotice) }
        assertThat(remote.gameLog.snapshot().map { it.event }).contains(expectedNotice)

        val unit = remote.stateFor(remote.playerId).unitsOf(PlayerId.PLAYER_2).first()
        val reachability = server.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id).first()
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
