package battletech.network

import battletech.network.client.RemoteGameSession
import battletech.network.server.GameServer
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.CommandResult
import battletech.tactical.session.CommitAttackImpulse
import battletech.tactical.session.CommitPhysicalAttackImpulse
import battletech.tactical.session.GameCommand
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.TurnEnded
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * End-to-end test over REAL TCP sockets (port 0 — OS-assigned, so parallel
 * test runs never collide) and REAL threads — the only layer-3 test that
 * skips both [PipedConnection] and [GameServer.attachTransport]. Plays a
 * full turn (MOVEMENT → WEAPON_ATTACK → PHYSICAL_ATTACK, then the automatic
 * HEAT → END → INITIATIVE cascade into turn 2's MOVEMENT) driven entirely
 * through [GameServer] (PLAYER_1, the host) and [RemoteGameSession]
 * (PLAYER_2, the joiner), asserting after every accepted command that the
 * remote replica has converged with the host on game state, turn state,
 * phase, and log length.
 *
 * Convergence timing differs by which side submitted, per the wire ordering
 * invariant documented on [RemoteGameSession]: a command submitted BY the
 * remote is guaranteed fresh the instant [RemoteGameSession.submitCommand]
 * returns (assert immediately, no poll needed); a command submitted by the
 * host reaches the remote asynchronously via a
 * [battletech.network.wire.ServerMessage.StatePush] on the remote's reader
 * thread, so convergence there is polled with a short deadline.
 *
 * No unit in [battletech.tactical.model.GameStateFactory.sampleGameState] is
 * ever prone during this test — falls only follow combat damage, and every
 * attack impulse here is empty — so [battletech.tactical.session.StandUp]
 * never comes up.
 */
internal class LocalhostEndToEndTest {

    private val sessionId = "TESTID"

    private var server: GameServer? = null
    private var client: RemoteGameSession? = null

    @AfterEach
    fun tearDown() {
        client?.close()
        server?.close()
    }

    @Test
    fun `joining over a real socket delivers the kickstart snapshot and stays converged`() {
        val host = startServer()
        val remote = connectClient(host)

        // The join-time advance() kickstart runs on the server's accept thread and its
        // StatePush reaches the client asynchronously — poll rather than assert immediately.
        awaitTrue { host.currentPhase == TurnPhase.MOVEMENT }

        awaitConvergence(host, remote)
        assertThat(remote.gameLog.snapshot()).isNotEmpty
        assertThat(remote.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
    }

    @Test
    fun `a full turn played alternately by host and remote converges after every accepted command`() {
        val host = startServer()
        val remote = connectClient(host)

        awaitTrue { host.currentPhase == TurnPhase.MOVEMENT }
        awaitConvergence(host, remote)

        playMovementPhase(host, remote)
        playAttackImpulses(host, remote, TurnPhase.WEAPON_ATTACK) { active ->
            CommitAttackImpulse(active, emptyList(), emptyMap())
        }
        playAttackImpulses(host, remote, TurnPhase.PHYSICAL_ATTACK) { active ->
            CommitPhysicalAttackImpulse(active, emptyList(), emptyMap())
        }

        // The final physical impulse cascades HEAT -> END -> INITIATIVE -> MOVEMENT
        // (all system phases; no further commands needed) and the turn number advances.
        assertThat(host.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(host.turnState.turnNumber).isEqualTo(2)
        assertThat(host.gameLog.snapshot().map { it.event })
            .anyMatch { it is TurnEnded && it.turnNumber == 1 }
        assertConverged(host, remote)
    }

    // ---------- setup ----------

    private fun startServer(): GameServer {
        val gameServer = GameServer(aSampleSession(), sessionId, port = 0)
        server = gameServer
        gameServer.start()
        return gameServer
    }

    private fun connectClient(host: GameServer): RemoteGameSession {
        val remote = RemoteGameSession.connect("127.0.0.1", host.boundPort, sessionId)
        client = remote
        return remote
    }

    // ---------- phase scripts ----------

    /**
     * Drives one [MoveUnit] per movement impulse until the phase advances.
     * Who is active is read from the host's authoritative [GameServer.turnState]
     * (both sides agree once converged, which they are at the top of every
     * iteration); PLAYER_2's move is built from the REMOTE's OWN
     * [RemoteGameSession.turnState]/[RemoteGameSession.viewFor] rather than the
     * host's, so the replica is proven independently queryable, not just a
     * pass-through.
     */
    private fun playMovementPhase(host: GameServer, remote: RemoteGameSession) {
        while (host.currentPhase == TurnPhase.MOVEMENT) {
            val active = host.turnState.movement.activePlayer
            val command = if (active == PlayerId.PLAYER_1) {
                val unit = host.turnState.selectableUnits(host.gameState).first()
                val reachability = host.viewFor(active).legalMovementsFor(unit.id).first()
                MoveUnit(active, unit.id, reachability.destinations.first(), reachability.mode)
            } else {
                val unit = remote.turnState.selectableUnits(remote.gameState).first()
                val reachability = remote.viewFor(active).legalMovementsFor(unit.id).first()
                MoveUnit(active, unit.id, reachability.destinations.first(), reachability.mode)
            }
            submitAndVerify(host, remote, active, command)
        }
    }

    /** Drives one impulse-commit [command] per attack impulse until [phase] advances. */
    private fun playAttackImpulses(
        host: GameServer,
        remote: RemoteGameSession,
        phase: TurnPhase,
        command: (PlayerId) -> GameCommand,
    ) {
        while (host.currentPhase == phase) {
            val active = host.turnState.attack.activePlayer
            submitAndVerify(host, remote, active, command(active))
        }
    }

    /**
     * Submits [command] via whichever side owns [actingSide] (host = PLAYER_1,
     * remote = PLAYER_2), asserts acceptance, then verifies convergence — polled
     * for a host-originated command, immediate for a remote-originated one (see
     * class doc for why the two differ).
     */
    private fun submitAndVerify(host: GameServer, remote: RemoteGameSession, actingSide: PlayerId, command: GameCommand) {
        val result = if (actingSide == PlayerId.PLAYER_1) host.submitCommand(command) else remote.submitCommand(command)
        assertThat(result).isInstanceOf(CommandResult.Accepted::class.java)
        if (actingSide == PlayerId.PLAYER_1) awaitConvergence(host, remote) else assertConverged(host, remote)
    }

    // ---------- convergence ----------

    private fun awaitConvergence(host: GameServer, remote: RemoteGameSession) {
        awaitTrue { converged(host, remote) }
        assertConverged(host, remote)
    }

    private fun converged(host: GameServer, remote: RemoteGameSession): Boolean =
        remote.gameState == host.gameState &&
            remote.turnState == host.turnState &&
            remote.currentPhase == host.currentPhase &&
            remote.gameLog.snapshot().size == host.gameLog.snapshot().size

    private fun assertConverged(host: GameServer, remote: RemoteGameSession) {
        assertThat(remote.gameState).isEqualTo(host.gameState)
        assertThat(remote.turnState).isEqualTo(host.turnState)
        assertThat(remote.currentPhase).isEqualTo(host.currentPhase)
        assertThat(remote.gameLog.snapshot()).hasSize(host.gameLog.snapshot().size)
    }
}
