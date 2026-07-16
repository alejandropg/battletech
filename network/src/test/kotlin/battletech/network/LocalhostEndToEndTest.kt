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
 * skips both [PipedConnection] and [GameServer.attach]. Plays a
 * full turn (MOVEMENT → WEAPON_ATTACK → PHYSICAL_ATTACK, then the automatic
 * HEAT → END → INITIATIVE cascade into turn 2's MOVEMENT) driven entirely
 * through [GameServer] (PLAYER_1, the host) and [RemoteGameSession]
 * (PLAYER_2, the joiner), asserting after every accepted command that the
 * remote replica has converged with the host on projected state, turn
 * state, phase, and log length.
 *
 * Convergence timing differs by which side submitted, per the wire ordering
 * invariant documented on [RemoteGameSession]: a command submitted BY the
 * remote is guaranteed fresh the instant [RemoteGameSession.submitCommand]
 * returns (assert immediately, no poll needed); a command submitted by the
 * host reaches the remote asynchronously via a
 * [battletech.network.wire.ServerMessage.StatePush] on the remote's reader
 * thread, so convergence there is polled with a short deadline.
 *
 * PLAYER_2's moves are built entirely from the REMOTE's own surface — its
 * [RemoteGameSession.stateFor] for the unit and its [RemoteGameSession.viewFor] for
 * reachability — never the host's. That is deliberate: routing client queries through
 * [GameServer.viewFor] would let a completely broken remote client pass this test, which is
 * exactly how a `--join`ed client that crashed on entering movement once slipped through a
 * green suite.
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
    private var client2: RemoteGameSession? = null

    @AfterEach
    fun tearDown() {
        client?.close()
        client2?.close()
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

    @Test
    fun `headless config - two real-socket RemoteGameSessions converge with the server after every accepted command`() {
        val gameServer = GameServer(
            aSampleSession(),
            sessionId,
            port = 0,
            remoteSeats = setOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2),
        )
        server = gameServer
        gameServer.start()

        val remote1 = RemoteGameSession.connect("127.0.0.1", gameServer.boundPort, sessionId)
        client = remote1
        val remote2 = RemoteGameSession.connect("127.0.0.1", gameServer.boundPort, sessionId)
        client2 = remote2

        assertThat(remote1.playerId).isEqualTo(PlayerId.PLAYER_1)
        assertThat(remote2.playerId).isEqualTo(PlayerId.PLAYER_2)

        // The join-time advance() kickstart runs on the server's accept thread and its
        // StatePush reaches both clients asynchronously — poll rather than assert immediately.
        awaitTrue { gameServer.currentPhase == TurnPhase.MOVEMENT }
        awaitHeadlessConvergence(gameServer, remote1, remote2)

        playHeadlessMovementPhase(gameServer, remote1, remote2)
        playHeadlessAttackImpulses(gameServer, remote1, remote2, TurnPhase.WEAPON_ATTACK) { active ->
            CommitAttackImpulse(active, emptyList(), emptyMap())
        }
        playHeadlessAttackImpulses(gameServer, remote1, remote2, TurnPhase.PHYSICAL_ATTACK) { active ->
            CommitPhysicalAttackImpulse(active, emptyList(), emptyMap())
        }

        // The final physical impulse cascades HEAT -> END -> INITIATIVE -> MOVEMENT
        // (all system phases; no further commands needed) and the turn number advances.
        assertThat(gameServer.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(gameServer.turnState.turnNumber).isEqualTo(2)
        assertThat(gameServer.gameLog.snapshot().map { it.event })
            .anyMatch { it is TurnEnded && it.turnNumber == 1 }
        assertHeadlessConverged(gameServer, remote1, remote2)
    }

    // ---------- headless-config phase scripts (both seats remote; no host.submitCommand path) ----------

    /**
     * Mirrors [playMovementPhase] but for the headless config: whichever
     * [RemoteGameSession] owns the active seat submits, and BOTH remotes are
     * checked for convergence with the server afterwards (unlike the
     * host-embedded scripts, there is no local host seat that's trivially
     * up to date). Both the unit and its reachability come from the acting REMOTE's own
     * surface — never the server's — so a client that couldn't answer locally would fail
     * here rather than pass on the host's coattails.
     */
    private fun playHeadlessMovementPhase(gameServer: GameServer, remote1: RemoteGameSession, remote2: RemoteGameSession) {
        while (gameServer.currentPhase == TurnPhase.MOVEMENT) {
            val active = gameServer.turnState.movement.activePlayer
            val actor = if (active == PlayerId.PLAYER_1) remote1 else remote2
            val unit = actor.turnState.selectableUnits(actor.stateFor(actor.playerId)).first()
            val reachability = actor.viewFor(active).legalMovementsFor(unit.id).first()
            val command = MoveUnit(active, unit.id, reachability.destinations.first(), reachability.mode)
            submitAndVerifyHeadless(gameServer, remote1, remote2, active, command)
        }
    }

    private fun playHeadlessAttackImpulses(
        gameServer: GameServer,
        remote1: RemoteGameSession,
        remote2: RemoteGameSession,
        phase: TurnPhase,
        command: (PlayerId) -> GameCommand,
    ) {
        while (gameServer.currentPhase == phase) {
            val active = gameServer.turnState.attack.activePlayer
            submitAndVerifyHeadless(gameServer, remote1, remote2, active, command(active))
        }
    }

    /**
     * Submits [command] via whichever remote owns [actingSide], asserts
     * acceptance, then polls for convergence: the submitting remote and the
     * server are already up to date synchronously (per the ordering
     * invariant on [RemoteGameSession]), but the OTHER remote only catches
     * up once its own reader thread applies the fan-out [StatePush] — so
     * every headless-config submit is polled, unlike the host-embedded
     * scripts where a host-originated command needs polling but a
     * remote-originated one doesn't.
     */
    private fun submitAndVerifyHeadless(
        gameServer: GameServer,
        remote1: RemoteGameSession,
        remote2: RemoteGameSession,
        actingSide: PlayerId,
        command: GameCommand,
    ) {
        val actor = if (actingSide == PlayerId.PLAYER_1) remote1 else remote2
        val result = actor.submitCommand(command)
        assertThat(result).isInstanceOf(CommandResult.Accepted::class.java)
        awaitHeadlessConvergence(gameServer, remote1, remote2)
    }

    private fun awaitHeadlessConvergence(gameServer: GameServer, remote1: RemoteGameSession, remote2: RemoteGameSession) {
        awaitTrue { headlessConverged(gameServer, remote1, remote2) }
        assertHeadlessConverged(gameServer, remote1, remote2)
    }

    private fun headlessConverged(gameServer: GameServer, remote1: RemoteGameSession, remote2: RemoteGameSession): Boolean =
        remote1.stateFor(remote1.playerId) == gameServer.stateFor(remote1.playerId) &&
            remote2.stateFor(remote2.playerId) == gameServer.stateFor(remote2.playerId) &&
            remote1.turnState == gameServer.turnState &&
            remote2.turnState == gameServer.turnState &&
            remote1.currentPhase == gameServer.currentPhase &&
            remote2.currentPhase == gameServer.currentPhase &&
            remote1.gameLog.snapshot().size == gameServer.gameLog.snapshot().size &&
            remote2.gameLog.snapshot().size == gameServer.gameLog.snapshot().size

    private fun assertHeadlessConverged(gameServer: GameServer, remote1: RemoteGameSession, remote2: RemoteGameSession) {
        assertThat(remote1.stateFor(remote1.playerId)).isEqualTo(gameServer.stateFor(remote1.playerId))
        assertThat(remote2.stateFor(remote2.playerId)).isEqualTo(gameServer.stateFor(remote2.playerId))
        assertThat(remote1.turnState).isEqualTo(gameServer.turnState)
        assertThat(remote2.turnState).isEqualTo(gameServer.turnState)
        assertThat(remote1.currentPhase).isEqualTo(gameServer.currentPhase)
        assertThat(remote2.currentPhase).isEqualTo(gameServer.currentPhase)
        assertThat(remote1.gameLog.snapshot()).hasSize(gameServer.gameLog.snapshot().size)
        assertThat(remote2.gameLog.snapshot()).hasSize(gameServer.gameLog.snapshot().size)
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
     * iteration); PLAYER_2's move is then built ENTIRELY from the remote's own
     * [RemoteGameSession.stateFor] and [RemoteGameSession.viewFor], proving the replica is
     * independently queryable rather than a pass-through — see class KDoc.
     */
    private fun playMovementPhase(host: GameServer, remote: RemoteGameSession) {
        while (host.currentPhase == TurnPhase.MOVEMENT) {
            val active = host.turnState.movement.activePlayer
            val command = if (active == PlayerId.PLAYER_1) {
                val unit = host.turnState.selectableUnits(host.gameState).first()
                val reachability = host.viewFor(active).legalMovementsFor(unit.id).first()
                MoveUnit(active, unit.id, reachability.destinations.first(), reachability.mode)
            } else {
                val unit = remote.turnState.selectableUnits(remote.stateFor(remote.playerId)).first()
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
        remote.stateFor(remote.playerId) == host.stateFor(remote.playerId) &&
            remote.turnState == host.turnState &&
            remote.currentPhase == host.currentPhase &&
            remote.gameLog.snapshot().size == host.gameLog.snapshot().size

    private fun assertConverged(host: GameServer, remote: RemoteGameSession) {
        assertThat(remote.stateFor(remote.playerId)).isEqualTo(host.stateFor(remote.playerId))
        assertThat(remote.turnState).isEqualTo(host.turnState)
        assertThat(remote.currentPhase).isEqualTo(host.currentPhase)
        assertThat(remote.gameLog.snapshot()).hasSize(host.gameLog.snapshot().size)
    }
}
