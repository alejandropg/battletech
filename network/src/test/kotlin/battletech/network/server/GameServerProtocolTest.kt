package battletech.network.server

import battletech.network.PipedConnection
import battletech.network.aSampleSession
import battletech.network.advanceMovementUntilActivePlayerIs
import battletech.network.attachInBackground
import battletech.network.awaitTrue
import battletech.network.join
import battletech.network.joinAndConsumeKickstart
import battletech.network.joinBothSeats
import battletech.network.wire.ClientMessage
import battletech.network.wire.JoinRejectionReason
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.network.wire.WireJson
import battletech.tactical.model.MechLocation
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.LogEntry
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.SessionNotice
import battletech.tactical.unit.CriticalSlotContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Drives [GameServer] over in-memory pipes ([PipedConnection]) via its
 * [GameServer.attach] testability seam — no real sockets. Covers
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
        assertThat(joinAccepted.log).containsExactly(LogEntry(turn = 1, event = SessionNotice("Player 2 connected")))
        assertThat(joinAccepted.snapshot.currentPhase).isEqualTo(TurnPhase.INITIATIVE)
        assertThat(joinAccepted.snapshot.gameState).isEqualTo(server.stateFor(PlayerId.PLAYER_2))

        assertThat(push.entries).isNotEmpty
        assertThat(push.snapshot.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(server.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
    }

    // ---------- the payoff: what a PLAYER_2 client actually receives on the wire ----------

    @Test
    fun `JoinAccepted's snapshot shows PLAYER_1's units as ForeignUnit and PLAYER_2's own as CombatUnit`() {
        val server = GameServer(aSampleSession(), sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val (joinAccepted, _) = connection.joinAndConsumeKickstart(sessionId)

        val units = joinAccepted.snapshot.gameState.units
        assertThat(units).isNotEmpty
        units.filter { it.owner == PlayerId.PLAYER_1 }.forEach { assertThat(it).isInstanceOf(ForeignUnit::class.java) }
        units.filter { it.owner == PlayerId.PLAYER_2 }.forEach { assertThat(it).isInstanceOf(battletech.tactical.unit.CombatUnit::class.java) }
    }

    @Test
    fun `JoinAccepted's log carries Undisclosed, not Detailed, for a PLAYER_1 CriticalHit`() {
        val session = aSampleSession()
        val player1Unit = session.gameState.unitsOf(PlayerId.PLAYER_1).first()
        // annotate() lands this in the same gameLog session.logFor (and this stage's
        // GameServer.snapshotFor/attach) redact through — no gameplay needed to
        // prove the redaction seam itself.
        session.annotate(
            CriticalHit.Detailed(
                unitId = player1Unit.id,
                location = MechLocation.LEFT_TORSO,
                slotIndex = 0,
                content = CriticalSlotContent.Empty,
            ),
        )
        val server = GameServer(session, sessionId, port = 0)
        val connection = PipedConnection()
        server.attachInBackground(connection)

        val (joinAccepted, _) = connection.joinAndConsumeKickstart(sessionId)

        val criticalEvents = joinAccepted.log.map { it.event }.filterIsInstance<CriticalHit>()
        assertThat(criticalEvents).hasSize(1)
        assertThat(criticalEvents.single()).isEqualTo(CriticalHit.Undisclosed(player1Unit.id))
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
        assertThat(server.gameLog.snapshot().map { it.event }).contains(SessionNotice("Player 2 connected"))

        firstConnection.closeClientSide()
        awaitTrue {
            server.gameLog.snapshot().map { it.event }
                .contains(SessionNotice("Player 2 disconnected — waiting for rejoin…"))
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
            "Player 2 connected",
            "Player 2 disconnected — waiting for rejoin…",
            "Player 2 connected",
        )

        // No second kickstart: give any wrongful push a generous window to arrive, then confirm none did.
        Thread.sleep(200)
        assertThat(server.gameLog.snapshot().size).isEqualTo(logSizeBeforeRejoin + 2)
        assertThat(secondConnection.clientInput.ready()).isFalse()
    }

    // ---------- headless config: both seats remote ----------

    private fun twoSeatServer(): GameServer =
        GameServer(aSampleSession(), sessionId, port = 0, remoteSeats = setOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2))

    @Test
    fun `two-seat config - first join seats PLAYER_1 without a kickstart, second seats PLAYER_2 and the kickstart pushes to both`() {
        val server = twoSeatServer()
        val first = PipedConnection()
        server.attachInBackground(first)

        val firstAccepted = first.join(sessionId) as ServerMessage.JoinAccepted
        assertThat(firstAccepted.playerId).isEqualTo(PlayerId.PLAYER_1)
        assertThat(firstAccepted.snapshot.currentPhase).isEqualTo(TurnPhase.INITIATIVE)

        // No kickstart yet: give a wrongful push a generous window, then confirm none arrived.
        Thread.sleep(200)
        assertThat(first.clientInput.ready()).isFalse()
        assertThat(server.currentPhase).isEqualTo(TurnPhase.INITIATIVE)

        val second = PipedConnection()
        server.attachInBackground(second)
        val secondAccepted = second.join(sessionId) as ServerMessage.JoinAccepted
        assertThat(secondAccepted.playerId).isEqualTo(PlayerId.PLAYER_2)

        val firstPush = WireJson.decodeServerMessage(first.clientInput.readLine()) as ServerMessage.StatePush
        val secondPush = WireJson.decodeServerMessage(second.clientInput.readLine()) as ServerMessage.StatePush
        assertThat(firstPush.snapshot.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(secondPush.snapshot.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(server.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
    }

    @Test
    fun `two-seat config - a third join attempt is rejected with SEAT_TAKEN`() {
        val server = twoSeatServer()
        val first = PipedConnection()
        val second = PipedConnection()
        server.attachInBackground(first)
        server.attachInBackground(second)
        joinBothSeats(sessionId, first, second)

        val third = PipedConnection()
        server.attachInBackground(third)
        val rejection = third.join(sessionId) as ServerMessage.JoinRejected

        assertThat(rejection.reason).isEqualTo(JoinRejectionReason.SEAT_TAKEN)
    }

    @Test
    fun `two-seat config - a command from the lone first joiner is rejected with OpponentUnavailable and leaves the session untouched`() {
        val server = twoSeatServer()
        val first = PipedConnection()
        server.attachInBackground(first)
        first.join(sessionId)
        val logSizeBefore = server.gameLog.snapshot().size

        val unit = server.gameState.unitsOf(PlayerId.PLAYER_1).first()
        val reachability = server.viewFor(PlayerId.PLAYER_1).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val command = MoveUnit(PlayerId.PLAYER_1, unit.id, destination, reachability.mode)

        first.clientOutput.write(WireJson.encodeToLine(ClientMessage.SubmitCommand(1L, command)) + "\n")
        first.clientOutput.flush()
        val reply = WireJson.decodeServerMessage(first.clientInput.readLine()) as ServerMessage.CommandReply

        assertThat(reply.result).isEqualTo(CommandResult.Rejected(CommandRejection.OpponentUnavailable))
        assertThat(server.gameLog.snapshot().size).isEqualTo(logSizeBefore)
    }

    @Test
    fun `two-seat config - a client seated PLAYER_1 sending a command as PLAYER_2 is rejected with NotYourTurn`() {
        val server = twoSeatServer()
        val first = PipedConnection()
        val second = PipedConnection()
        server.attachInBackground(first)
        server.attachInBackground(second)
        joinBothSeats(sessionId, first, second)

        val unit = server.gameState.unitsOf(PlayerId.PLAYER_2).first()
        val reachability = server.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val command = MoveUnit(PlayerId.PLAYER_2, unit.id, destination, reachability.mode)

        first.clientOutput.write(WireJson.encodeToLine(ClientMessage.SubmitCommand(9L, command)) + "\n")
        first.clientOutput.flush()
        val reply = WireJson.decodeServerMessage(first.clientInput.readLine()) as ServerMessage.CommandReply

        val rejected = reply.result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.NotYourTurn::class.java)
        val notYourTurn = rejected.reason as CommandRejection.NotYourTurn
        assertThat(notYourTurn.activePlayer).isEqualTo(PlayerId.PLAYER_1)
        assertThat(notYourTurn.attemptedBy).isEqualTo(PlayerId.PLAYER_2)
    }

    @Test
    fun `two-seat config - an accepted command from one client pushes state to both clients, push before reply to the submitter`() {
        val server = twoSeatServer()
        val first = PipedConnection()
        val second = PipedConnection()
        server.attachInBackground(first)
        server.attachInBackground(second)
        joinBothSeats(sessionId, first, second)

        val active = server.turnState.movement.activePlayer
        val submitter = if (active == PlayerId.PLAYER_1) first else second
        val other = if (active == PlayerId.PLAYER_1) second else first
        val unit = server.turnState.selectableUnits(server.gameState).first()
        val reachability = server.viewFor(active).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val command = MoveUnit(active, unit.id, destination, reachability.mode)

        submitter.clientOutput.write(WireJson.encodeToLine(ClientMessage.SubmitCommand(1L, command)) + "\n")
        submitter.clientOutput.flush()

        val submitterFirstLine = WireJson.decodeServerMessage(submitter.clientInput.readLine())
        val submitterSecondLine = WireJson.decodeServerMessage(submitter.clientInput.readLine())
        assertThat(submitterFirstLine).isInstanceOf(ServerMessage.StatePush::class.java)
        assertThat(submitterSecondLine).isInstanceOf(ServerMessage.CommandReply::class.java)
        assertThat((submitterSecondLine as ServerMessage.CommandReply).result).isInstanceOf(CommandResult.Accepted::class.java)

        val otherPush = WireJson.decodeServerMessage(other.clientInput.readLine())
        assertThat(otherPush).isInstanceOf(ServerMessage.StatePush::class.java)
    }

    @Test
    fun `two-seat config - disconnecting one seat freezes the other, and rejoin on a new connection reclaims the vacant seat without a second kickstart`() {
        val server = twoSeatServer()
        val first = PipedConnection()
        val second = PipedConnection()
        server.attachInBackground(first)
        server.attachInBackground(second)
        val bothJoined = joinBothSeats(sessionId, first, second)
        assertThat(bothJoined.firstAccepted.playerId).isEqualTo(PlayerId.PLAYER_1)
        val logSizeBeforeDisconnect = server.gameLog.snapshot().size

        first.closeClientSide()
        awaitTrue {
            server.gameLog.snapshot().map { it.event }
                .contains(SessionNotice("Player 1 disconnected — waiting for rejoin…"))
        }

        val unit = server.gameState.unitsOf(PlayerId.PLAYER_2).first()
        val reachability = server.viewFor(PlayerId.PLAYER_2).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val command = MoveUnit(PlayerId.PLAYER_2, unit.id, destination, reachability.mode)
        second.clientOutput.write(WireJson.encodeToLine(ClientMessage.SubmitCommand(2L, command)) + "\n")
        second.clientOutput.flush()
        val reply = WireJson.decodeServerMessage(second.clientInput.readLine()) as ServerMessage.CommandReply
        assertThat(reply.result).isEqualTo(CommandResult.Rejected(CommandRejection.OpponentUnavailable))

        val rejoin = PipedConnection()
        server.attachInBackground(rejoin)
        val rejoinAccepted = rejoin.join(sessionId) as ServerMessage.JoinAccepted

        assertThat(rejoinAccepted.playerId).isEqualTo(PlayerId.PLAYER_1)
        // +1 for the disconnect notice, +1 for the rejoin's own "Player 1 connected" notice.
        assertThat(rejoinAccepted.log).hasSize(logSizeBeforeDisconnect + 2)
        val noticeTexts = rejoinAccepted.log.map { it.event }.filterIsInstance<SessionNotice>().map { it.text }
        assertThat(noticeTexts).containsSequence(
            "Player 1 disconnected — waiting for rejoin…",
            "Player 1 connected",
        )

        // No second kickstart: give any wrongful push a generous window to arrive, then confirm none did.
        Thread.sleep(200)
        assertThat(rejoin.clientInput.ready()).isFalse()
    }
}
