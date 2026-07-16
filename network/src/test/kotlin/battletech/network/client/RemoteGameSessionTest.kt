package battletech.network.client

import battletech.network.PipedConnection
import battletech.network.aSampleSession
import battletech.network.advanceMovementUntilActivePlayerIs
import battletech.network.attachInBackground
import battletech.network.awaitTrue
import battletech.network.server.GameServer
import battletech.network.transport.JsonLineConnection
import battletech.network.wire.ClientMessage
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.GameEvent
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.UnitMoved
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Drives [RemoteGameSession] over in-memory pipes against a real
 * [GameServer] (via its [GameServer.attach] testability seam) — no
 * real sockets. Every test below calls [GameServer.connectLocal] to claim PLAYER_1 before
 * attaching its [PipedConnection] — [GameServer] now expects a connection for every seat before
 * it will kick off the match, so this is what makes the lone [PipedConnection] land on PLAYER_2
 * and the kickstart actually fire. Covers post-join state, local queries against the snapshot,
 * event dispatch, the push-before-reply ordering invariant from the client
 * side, connection-loss handling, and — the point of this stage — that the
 * snapshot/log this replica holds is already redacted for [RemoteGameSession.playerId],
 * never PLAYER_1's private fields.
 */
internal class RemoteGameSessionTest {

    private val sessionId = "TESTID"

    @Test
    fun `after join, stateFor turnState and currentPhase reflect the post-kickstart snapshot`() {
        val server = GameServer(aSampleSession(), sessionId)
        server.connectLocal()
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
        val server = GameServer(aSampleSession(), sessionId)
        server.connectLocal()
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val remote = connectRemoteOverPipes(sessionId, connection)

        assertThat(remote.playerId).isEqualTo(PlayerId.PLAYER_2)
    }

    // ---------- the payoff: what this client actually holds is already redacted ----------

    @Test
    fun `stateFor(playerId) shows PLAYER_1's units as ForeignUnit and this seat's own as CombatUnit`() {
        val server = GameServer(aSampleSession(), sessionId)
        server.connectLocal()
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        val units = remote.stateFor(remote.playerId).units
        assertThat(units).isNotEmpty
        units.filter { it.owner == PlayerId.PLAYER_1 }.forEach { assertThat(it).isInstanceOf(ForeignUnit::class.java) }
        units.filter { it.owner == PlayerId.PLAYER_2 }.forEach { assertThat(it).isInstanceOf(battletech.tactical.unit.CombatUnit::class.java) }
    }

    @Test
    fun `stateFor and logFor refuse a viewer other than this replica's own seat`() {
        val server = GameServer(aSampleSession(), sessionId)
        server.connectLocal()
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        assertThatThrownBy { remote.stateFor(PlayerId.PLAYER_1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { remote.stateFor(null) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { remote.logFor(PlayerId.PLAYER_1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { remote.logFor(null) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    /**
     * The regression guard for remote play: a `--join`ed client must be able to answer
     * "what is legal right now?" from its OWN projected snapshot, with no round trip and
     * WITHOUT borrowing the host's [GameServer.viewFor]. Routing this through the server
     * is the shortcut that previously hid a totally broken remote client behind a green
     * suite — every assertion below is against `remote.viewFor`, deliberately.
     */
    @Test
    fun `viewFor on the remote's own seat answers movement legality from its projected snapshot`() {
        val server = GameServer(aSampleSession(), sessionId)
        server.connectLocal()
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        val unit = remote.stateFor(remote.playerId).unitsOf(PlayerId.PLAYER_2).first()
        val reachability = remote.viewFor(remote.playerId).legalMovementsFor(unit.id)

        assertThat(reachability).isNotEmpty
        assertThat(reachability.first().destinations).isNotEmpty
        // The client computed the same answer the authoritative host would.
        assertThat(reachability).isEqualTo(server.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id))
    }

    @Test
    fun `viewFor on the remote's own seat answers targeting queries from its projected snapshot`() {
        val server = GameServer(aSampleSession(), sessionId)
        server.connectLocal()
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        val unit = remote.stateFor(remote.playerId).unitsOf(PlayerId.PLAYER_2).first()
        val view = remote.viewFor(remote.playerId)

        // Fire arc + legal torso facings are pure geometry off the projection.
        assertThat(view.fireArc(unit.id, unit.facing)).isNotEmpty
        assertThat(view.legalTorsoFacings(unit.id)).isNotEmpty
        // And they agree with the host's authoritative answers.
        assertThat(view.fireArc(unit.id, unit.facing))
            .isEqualTo(server.viewFor(PlayerId.PLAYER_2).fireArc(unit.id, unit.facing))
        assertThat(view.targetInfos(unit.id, unit.facing))
            .isEqualTo(server.viewFor(PlayerId.PLAYER_2).targetInfos(unit.id, unit.facing))
    }

    @Test
    fun `viewFor still refuses a seat other than this replica's own`() {
        val server = GameServer(aSampleSession(), sessionId)
        server.connectLocal()
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }

        assertThatThrownBy { remote.viewFor(PlayerId.PLAYER_1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a subscribed listener receives events pushed by the host`() {
        val server = GameServer(aSampleSession(), sessionId)
        val local = server.connectLocal()
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
        val result = local.submitCommand(MoveUnit(active, unit.id, destination, reachability.mode))
        check(result is CommandResult.Accepted) { "setup move rejected: $result" }

        awaitTrue { events.any { it is UnitMoved } }
    }

    @Test
    fun `submitCommand returns the reply result and the snapshot is already fresh when it returns`() {
        val server = GameServer(aSampleSession(), sessionId)
        val local = server.connectLocal()
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }
        local.advanceMovementUntilActivePlayerIs(PlayerId.PLAYER_2)
        awaitTrue { remote.turnState.movement.activePlayer == PlayerId.PLAYER_2 }

        // Built entirely from the replica's own surface — no host queries — so this exercises
        // the real client path a --join'ed seat takes.
        val unit = remote.turnState.selectableUnits(remote.stateFor(remote.playerId)).first()
        val reachability = remote.viewFor(remote.playerId).legalMovementsFor(unit.id).first()
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
    fun `submitCommand with an unknown unit id gets ProtocolError and the connection stays usable`() {
        val server = GameServer(aSampleSession(), sessionId)
        val local = server.connectLocal()
        val connection = PipedConnection()
        server.attachInBackground(connection)
        val remote = connectRemoteOverPipes(sessionId, connection)
        awaitTrue { remote.currentPhase == TurnPhase.MOVEMENT }
        local.advanceMovementUntilActivePlayerIs(PlayerId.PLAYER_2)
        awaitTrue { remote.turnState.movement.activePlayer == PlayerId.PLAYER_2 }

        // A correctly-behaving client can never produce this — GameServer's reader loop
        // catches the resulting UnknownUnitException instead of letting it tear the reader
        // thread (and connection) down. See UnknownUnitException, CommandResult.ProtocolError.
        val bogusMove = MoveUnit(
            PlayerId.PLAYER_2,
            UnitId("ghost"),
            remote.viewFor(remote.playerId)
                .legalMovementsFor(remote.turnState.selectableUnits(remote.stateFor(remote.playerId)).first().id)
                .first()
                .destinations
                .first(),
            MovementMode.WALK,
        )

        val result = remote.submitCommand(bogusMove)

        assertThat(result).isInstanceOf(CommandResult.ProtocolError::class.java)

        // The connection survived: a legitimate move right after still goes through.
        val unit = remote.turnState.selectableUnits(remote.stateFor(remote.playerId)).first()
        val reachability = remote.viewFor(remote.playerId).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val followUp = remote.submitCommand(MoveUnit(PlayerId.PLAYER_2, unit.id, destination, reachability.mode))

        assertThat(followUp).isInstanceOf(CommandResult.Accepted::class.java)
    }

    @Test
    fun `connection lost appends a SessionNotice to the replica log and delivers it to subscribers, then submitCommand rejects with OpponentUnavailable`() {
        val server = GameServer(aSampleSession(), sessionId)
        server.connectLocal()
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
        val reachability = remote.viewFor(remote.playerId).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val result = remote.submitCommand(MoveUnit(PlayerId.PLAYER_2, unit.id, destination, reachability.mode))

        assertThat(result).isEqualTo(CommandResult.Rejected(CommandRejection.OpponentUnavailable))
    }

    /** Mirrors [RemoteGameSession.connect] but over [PipedConnection] pipes instead of a real socket. */
    private fun connectRemoteOverPipes(sessionId: String, connection: PipedConnection): RemoteGameSession {
        val jsonConnection = JsonLineConnection.Client(connection.clientInput, connection.clientOutput)
        jsonConnection.send(ClientMessage.Join(sessionId, PROTOCOL_VERSION))
        val message = jsonConnection.receive() ?: error("connection closed before join response")
        return when (message) {
            is ServerMessage.JoinAccepted -> RemoteGameSession(jsonConnection, message)
            is ServerMessage.JoinRejected -> throw JoinRejectedException(message.reason)
            else -> error("unexpected first message from host: $message")
        }
    }
}
