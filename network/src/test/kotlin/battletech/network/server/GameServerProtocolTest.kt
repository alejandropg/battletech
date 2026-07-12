package battletech.network.server

import battletech.network.PipedConnection
import battletech.network.aSampleSession
import battletech.network.advanceMovementUntilActivePlayerIs
import battletech.network.attachInBackground
import battletech.network.awaitTrue
import battletech.network.join
import battletech.network.joinAndConsumeKickstart
import battletech.network.wire.ClientMessage
import battletech.network.wire.JoinRejectionReason
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.network.wire.WireJson
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.LogEntry
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.SessionNotice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Drives [GameServer] over in-memory pipes ([PipedConnection]) via its
 * [GameServer.attachTransport] testability seam — no real sockets. Covers
 * the join handshake, the wire ordering invariant (push before reply),
 * remote player-id spoofing, the host's own [GameServer.submitCommand]
 * path, and disconnect/rejoin.
 */
internal class GameServerProtocolTest {

    private val sessionId = "TESTID"

    @Test
    fun `successful join sends JoinAccepted for PLAYER_2 with a pre-kickstart snapshot and a connect notice in the log`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val (joinAccepted, push) = connection.joinAndConsumeKickstart(sessionId)

        assertThat(joinAccepted.playerId).isEqualTo(PlayerId.PLAYER_2)
        assertThat(joinAccepted.log).containsExactly(LogEntry(turn = 1, event = SessionNotice("Opponent connected")))
        assertThat(joinAccepted.snapshot.currentPhase).isEqualTo(TurnPhase.INITIATIVE)
        assertThat(joinAccepted.snapshot.gameState).isEqualTo(GameStateFactory().sampleGameState())

        assertThat(push.entries).isNotEmpty
        assertThat(push.snapshot.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(server.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
    }

    @Test
    fun `wrong session id rejects with UNKNOWN_SESSION, and the server still accepts a later correct join`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)

        val badConnection = PipedConnection()
        server.attachInBackground(badConnection)
        val rejection = badConnection.join("WRONGID") as ServerMessage.JoinRejected
        assertThat(rejection.reason).isEqualTo(JoinRejectionReason.UNKNOWN_SESSION)

        val goodConnection = PipedConnection()
        server.attachInBackground(goodConnection)
        val accepted = goodConnection.join(sessionId)
        assertThat(accepted).isInstanceOf(ServerMessage.JoinAccepted::class.java)
    }

    @Test
    fun `wrong protocol version rejects with INCOMPATIBLE_PROTOCOL`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val rejection = connection.join(sessionId, protocolVersion = PROTOCOL_VERSION + 1) as ServerMessage.JoinRejected

        assertThat(rejection.reason).isEqualTo(JoinRejectionReason.INCOMPATIBLE_PROTOCOL)
    }

    @Test
    fun `remote command accepted pushes state to the wire before the command reply`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)
        connection.joinAndConsumeKickstart(sessionId)
        val setupMoves = server.advanceMovementUntilActivePlayerIs(PlayerId.PLAYER_2)
        repeat(setupMoves) {
            val setupPush = WireJson.decodeServerMessage(connection.clientInput.readLine())
            assertThat(setupPush).isInstanceOf(ServerMessage.StatePush::class.java)
        }

        val unit = server.turnState.selectableUnits(server.gameState).first()
        val reachability = server.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val command = MoveUnit(PlayerId.PLAYER_2, unit.id, destination, reachability.mode)

        connection.clientOutput.write(WireJson.encodeToLine(ClientMessage.SubmitCommand(1L, command)) + "\n")
        connection.clientOutput.flush()

        val first = WireJson.decodeServerMessage(connection.clientInput.readLine())
        val second = WireJson.decodeServerMessage(connection.clientInput.readLine())

        assertThat(first).isInstanceOf(ServerMessage.StatePush::class.java)
        assertThat((first as ServerMessage.StatePush).entries).isNotEmpty
        assertThat(second).isInstanceOf(ServerMessage.CommandReply::class.java)
        val reply = second as ServerMessage.CommandReply
        assertThat(reply.requestId).isEqualTo(1L)
        assertThat(reply.result).isInstanceOf(CommandResult.Accepted::class.java)
    }

    @Test
    fun `remote command spoofing PLAYER_1 is rejected without touching the session`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)
        connection.joinAndConsumeKickstart(sessionId)
        val logSizeBefore = server.gameLog.snapshot().size

        val unit = server.gameState.unitsOf(PlayerId.PLAYER_1).first()
        val reachability = server.viewFor(PlayerId.PLAYER_1).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val command = MoveUnit(PlayerId.PLAYER_1, unit.id, destination, reachability.mode)

        connection.clientOutput.write(WireJson.encodeToLine(ClientMessage.SubmitCommand(2L, command)) + "\n")
        connection.clientOutput.flush()
        val reply = WireJson.decodeServerMessage(connection.clientInput.readLine()) as ServerMessage.CommandReply

        assertThat(reply.requestId).isEqualTo(2L)
        val rejected = reply.result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.NotYourTurn::class.java)
        val notYourTurn = rejected.reason as CommandRejection.NotYourTurn
        assertThat(notYourTurn.activePlayer).isEqualTo(PlayerId.PLAYER_2)
        assertThat(notYourTurn.attemptedBy).isEqualTo(PlayerId.PLAYER_1)
        assertThat(server.gameLog.snapshot().size).isEqualTo(logSizeBefore)
    }

    @Test
    fun `host submitCommand rejects with OpponentUnavailable when no client is connected`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val unit = server.gameState.unitsOf(PlayerId.PLAYER_1).first()
        val reachability = server.viewFor(PlayerId.PLAYER_1).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()

        val result = server.submitCommand(MoveUnit(PlayerId.PLAYER_1, unit.id, destination, reachability.mode))

        assertThat(result).isEqualTo(CommandResult.Rejected(CommandRejection.OpponentUnavailable))
    }

    @Test
    fun `host submitCommand spoofing PLAYER_2 is rejected without touching the session or pushing state`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)
        connection.joinAndConsumeKickstart(sessionId)
        val logSizeBefore = server.gameLog.snapshot().size

        val unit = server.gameState.unitsOf(PlayerId.PLAYER_2).first()
        val reachability = server.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val command = MoveUnit(PlayerId.PLAYER_2, unit.id, destination, reachability.mode)

        val result = server.submitCommand(command)

        val rejected = result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.NotYourTurn::class.java)
        val notYourTurn = rejected.reason as CommandRejection.NotYourTurn
        assertThat(notYourTurn.activePlayer).isEqualTo(PlayerId.PLAYER_1)
        assertThat(notYourTurn.attemptedBy).isEqualTo(PlayerId.PLAYER_2)
        assertThat(server.gameLog.snapshot().size).isEqualTo(logSizeBefore)

        // No wrongful push should reach the wire: give it a generous window, then confirm none arrived.
        Thread.sleep(200)
        assertThat(connection.clientInput.ready()).isFalse()
    }

    @Test
    fun `host submitCommand with a client connected pushes state to the client`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)
        connection.joinAndConsumeKickstart(sessionId)

        val active = server.turnState.movement.activePlayer
        val unit = server.turnState.selectableUnits(server.gameState).first()
        val reachability = server.viewFor(active).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()

        val result = server.submitCommand(MoveUnit(active, unit.id, destination, reachability.mode))
        assertThat(result).isInstanceOf(CommandResult.Accepted::class.java)

        val push = WireJson.decodeServerMessage(connection.clientInput.readLine()) as ServerMessage.StatePush
        assertThat(push.entries).isNotEmpty
    }

    @Test
    fun `disconnect freezes the host, and rejoin resends the full log without re-running the kickstart`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val firstConnection = PipedConnection()
        server.attachInBackground(firstConnection)
        firstConnection.joinAndConsumeKickstart(sessionId)
        val logSizeBeforeRejoin = server.gameLog.snapshot().size
        assertThat(server.gameLog.snapshot().map { it.event }).contains(SessionNotice("Opponent connected"))

        firstConnection.closeClientSide()
        awaitTrue {
            server.gameLog.snapshot().map { it.event }
                .contains(SessionNotice("Opponent disconnected — waiting for rejoin…"))
        }

        val unit = server.gameState.unitsOf(PlayerId.PLAYER_1).first()
        val reachability = server.viewFor(PlayerId.PLAYER_1).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val frozenResult = server.submitCommand(MoveUnit(PlayerId.PLAYER_1, unit.id, destination, reachability.mode))
        assertThat(frozenResult).isEqualTo(CommandResult.Rejected(CommandRejection.OpponentUnavailable))

        val secondConnection = PipedConnection()
        server.attachInBackground(secondConnection)
        val rejoinAccepted = secondConnection.join(sessionId) as ServerMessage.JoinAccepted

        assertThat(rejoinAccepted.playerId).isEqualTo(PlayerId.PLAYER_2)
        assertThat(rejoinAccepted.log).hasSize(logSizeBeforeRejoin + 2)
        val noticeTexts = rejoinAccepted.log.map { it.event }.filterIsInstance<SessionNotice>().map { it.text }
        assertThat(noticeTexts).containsExactly(
            "Opponent connected",
            "Opponent disconnected — waiting for rejoin…",
            "Opponent connected",
        )

        // No second kickstart: give any wrongful push a generous window to arrive, then confirm none did.
        Thread.sleep(200)
        assertThat(server.gameLog.snapshot().size).isEqualTo(logSizeBeforeRejoin + 2)
        assertThat(secondConnection.clientInput.ready()).isFalse()
    }
}
