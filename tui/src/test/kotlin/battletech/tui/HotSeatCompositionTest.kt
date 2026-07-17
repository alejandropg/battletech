package battletech.tui

import battletech.network.client.ClientGameSession
import battletech.network.server.GameServer
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.BattleSession
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ForeignUnit
import battletech.tui.game.AppState
import battletech.tui.game.mapToTuiPhase
import battletech.tui.game.phase.BOARD_ORIGIN_X
import battletech.tui.game.phase.BOARD_ORIGIN_Y
import battletech.tui.hex.HexLayout
import battletech.tui.loop.UiEvent
import battletech.tui.loop.runLoop
import battletech.tui.screen.ScreenRenderer
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Exercises the REAL hot-seat composition — [GameServer.host]-shaped ([GameServer] + two
 * [GameServer.connectLocal] clients, no socket) — the thing [Main.kt][battletech.tui.Main]
 * actually builds and [battletech.tui.game.AppStateFactory]'s KDoc explains why the ~125
 * UI-logic tests deliberately do NOT: that factory hands both seats the SAME [BattleSession],
 * which projects for any viewer on request, so it cannot tell a real "seat A's own client
 * refuses to answer for seat B" bug from a working one. This class is where that distinction
 * gets checked, with two independently-projecting [ClientGameSession]s and their own reader
 * threads, properly closed in [tearDown].
 *
 * A seeded [RandomDiceRoller] ([Random]`(42L)`, mirroring `network`'s own
 * `SessionTestSupport.aSampleSession`) makes the movement impulse order deterministic across
 * runs — not that any test here depends on WHICH seat moves first, only that impulses
 * alternate one seat at a time once movement starts (see [calculateMovementOrder][battletech.tactical.session.calculateMovementOrder]:
 * with two units per side, `rounds == 2` and each impulse carries exactly one unit), so a
 * single accepted move always swings [TurnState.movement][battletech.tactical.session.TurnState.movement]`.activePlayer`
 * to the other seat.
 *
 * Reuses [GameStateFactory.sampleGameState]'s default 10x10 map/roster (the same one
 * `network`'s [aSampleSession][battletech.network.aSampleSession] and
 * [LocalhostEndToEndTest][battletech.network.LocalhostEndToEndTest] already build sessions
 * over) rather than fabricating a smaller one — it is already small, and reusing it means one
 * fewer bespoke fixture to keep in sync with the engine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HotSeatCompositionTest {

    private val recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 120, height = 40)
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = recorder)
    private val renderer = ScreenRenderer(terminal)

    private lateinit var server: GameServer
    private lateinit var seats: Map<PlayerId, ClientGameSession>

    @BeforeEach
    fun setUp() {
        val session = BattleSession(
            initialGameState = GameStateFactory().sampleGameState(),
            roller = RandomDiceRoller(Random(42L)),
        )
        server = GameServer(session = session, sessionId = "TESTID")
        // Mirrors Main.kt's Mode.Local branch exactly: build the roster, then await the
        // kickstart before anything reads currentPhase/turnState off a seat.
        seats = List(PlayerId.entries.size) { server.connectLocal() }.associateBy { it.playerId }
        awaitKickstart(server, seats)
    }

    @AfterEach
    fun tearDown() {
        // Close the clients first, then the server — mirrors LocalhostEndToEndTest's tearDown
        // order. Both sides are idempotent to close twice (see the dedicated close test below),
        // so a test that already closed everything itself does not fail here.
        seats.values.forEach { it.close() }
        server.close()
    }

    @Test
    fun `the kickstart reaches both seats and agrees with the server`() {
        assertThat(server.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        seats.values.forEach { seat ->
            assertThat(seat.currentPhase).isEqualTo(TurnPhase.MOVEMENT)
        }
    }

    /**
     * The thing [battletech.tui.game.AppStateFactory]'s shared-[BattleSession] fixture
     * structurally cannot prove: each seat's OWN [ClientGameSession] only ever holds ITS OWN
     * projection (see [ClientGameSession.stateFor]'s KDoc), so redaction here is enforced by
     * which object answers the query, not by an argument passed to a shared session.
     */
    @Test
    fun `redaction is real per seat`() {
        val p1Owned = seats.getValue(PlayerId.PLAYER_1).stateFor(PlayerId.PLAYER_1).unitsOf(PlayerId.PLAYER_1)
        val p1Foreign = seats.getValue(PlayerId.PLAYER_1).stateFor(PlayerId.PLAYER_1).unitsOf(PlayerId.PLAYER_2)
        assertThat(p1Owned).isNotEmpty.allSatisfy { assertThat(it).isInstanceOf(CombatUnit::class.java) }
        assertThat(p1Foreign).isNotEmpty.allSatisfy { assertThat(it).isInstanceOf(ForeignUnit::class.java) }

        val p2Owned = seats.getValue(PlayerId.PLAYER_2).stateFor(PlayerId.PLAYER_2).unitsOf(PlayerId.PLAYER_2)
        val p2Foreign = seats.getValue(PlayerId.PLAYER_2).stateFor(PlayerId.PLAYER_2).unitsOf(PlayerId.PLAYER_1)
        assertThat(p2Owned).isNotEmpty.allSatisfy { assertThat(it).isInstanceOf(CombatUnit::class.java) }
        assertThat(p2Foreign).isNotEmpty.allSatisfy { assertThat(it).isInstanceOf(ForeignUnit::class.java) }
    }

    @Test
    fun `a seat cannot read another seat's projection`() {
        assertThatThrownBy { seats.getValue(PlayerId.PLAYER_1).stateFor(PlayerId.PLAYER_2) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { seats.getValue(PlayerId.PLAYER_2).stateFor(PlayerId.PLAYER_1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    /**
     * Drives both seats through [runLoop] exactly as [battletech.tui.TuiApp.run] does: one
     * subscription per seat feeding [UiEvent.Session] back through [internalEvents]. A scripted
     * keyboard/mouse sequence selects the active seat's unit, clicks a legal destination hex
     * (translated to screen coordinates via [HexLayout.hexToScreen] — the exact inverse of the
     * click handling under test), and confirms the move — landing a real [battletech.tactical.session.MoveUnit]
     * through that seat's own [ClientGameSession.submitCommand].
     *
     * The submitting seat's own replica is synchronously fresh once `submitCommand` returns
     * (the StatePush-before-CommandReply wire invariant — see [ClientGameSession]'s KDoc), but
     * [AppState.anySession] may resolve to the OTHER seat, whose replica only catches up once
     * ITS OWN reader thread applies the fan-out push. So this polls for both seats' `activePlayer`
     * to swing to the opponent rather than asserting immediately — the exact hazard documented on
     * [battletech.tui.Main]'s `awaitKickstart` and exercised end-to-end here for the first time.
     */
    @Test
    fun `the TUI drives both seats through the real path - a move lands, the view swings, no waiting-for-opponent flash`() =
        runTest(UnconfinedTestDispatcher()) {
            val mover = server.turnState.movement.activePlayer
            val opponent = PlayerId.entries.single { it != mover }
            val moverSeat = seats.getValue(mover)

            val unit = moverSeat.turnState.selectableUnits(moverSeat.stateFor(mover)).first()
            val reachability = moverSeat.viewFor(mover).legalMovementsFor(unit.id).first()
            // Prefer an actual step (not "turn in place at the current hex", which can offer
            // several facings for zero MP) so exactly one facing is legal there and Enter commits
            // the move directly instead of opening the facing sub-menu.
            val destination = reachability.destinations
                .filter { it.position != unit.position }
                .firstOrNull { candidate -> reachability.destinations.count { it.position == candidate.position } == 1 }
                ?: reachability.destinations.first()
            val (screenX, screenY) = HexLayout.hexToScreen(destination.position.col, destination.position.row)

            val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)
            val subscriptions = seats.values.map { seat -> seat.subscribe { internalEvents.trySend(UiEvent.Session(it)) } }

            val initialState = AppState(
                seats = seats,
                phase = mapToTuiPhase(server.currentPhase),
                cursor = unit.position,
            )

            val loopJob = launch {
                runLoop(
                    events = internalEvents.receiveAsFlow(),
                    internalEvents = internalEvents,
                    terminal = terminal,
                    renderer = renderer,
                    initialState = initialState,
                )
            }

            try {
                // Select the mover's unit under the cursor -> enters Browsing.
                internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))
                // Click the destination hex -> sets hoveredDestination.
                internalEvents.send(
                    UiEvent.Input(MouseEvent(x = BOARD_ORIGIN_X + screenX, y = BOARD_ORIGIN_Y + screenY, left = true)),
                )
                // Confirm the path -> submits MoveUnit through moverSeat's REAL ClientGameSession.
                internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))

                awaitCondition {
                    server.turnState.movement.activePlayer == opponent &&
                        seats.values.all { it.activePlayer == opponent }
                }

                // Drain any resync UiEvent.Session the passive seat's subscription fired after
                // the poll above, so the recorded output reflects the fully-converged frame.
                internalEvents.send(UiEvent.Resized(Size(120, 40)))

                assertThat(server.stateFor(mover).unitById(unit.id).position).isEqualTo(destination.position)
                assertThat(recorder.output()).doesNotContain("Waiting for opponent")

                // AppState.viewer follows the active player across real client sessions — the
                // property that only holds because both real seats are in the map.
                val resynced = AppState(seats = seats, phase = mapToTuiPhase(server.currentPhase), cursor = destination.position)
                assertThat(resynced.viewer).isEqualTo(opponent)
            } finally {
                subscriptions.forEach { it.unsubscribe() }
                internalEvents.send(UiEvent.Quit)
                loopJob.join()
            }
        }

    @Test
    fun `closing the server and clients leaves no client-reader or server threads running`() {
        seats.values.forEach { it.close() }
        server.close()

        val leftoverNames = setOf("client-session-reader", "game-server-local", "game-server-write")
        awaitCondition {
            Thread.getAllStackTraces().keys.none { it.name in leftoverNames }
        }
        assertThat(Thread.getAllStackTraces().keys.filter { it.name in leftoverNames }).isEmpty()
    }

    // ---- fixtures ----

    /**
     * Mirrors [battletech.tui.Main]'s private `awaitKickstart`: [GameServer.connectLocal] can
     * return before the roster-completing [BattleSession.advance] kickstart has been applied by
     * that seat's own reader thread (see that function's KDoc for the full race). Bounded so a
     * genuine hang fails loudly instead of wedging the suite.
     */
    private fun awaitKickstart(server: GameServer, seats: Map<PlayerId, ClientGameSession>) {
        awaitCondition { seats.values.all { it.currentPhase == server.currentPhase } }
    }

    /**
     * `network`'s test-only `awaitTrue` (`network/src/test/kotlin/battletech/network/SessionTestSupport.kt`)
     * is `internal` to `network`'s OWN test source set/compilation, so it is not on `tui`'s test
     * classpath at all — this is the "small bounded equivalent" `tui` needs of its own.
     */
    private fun awaitCondition(timeoutMs: Long = 2_000, intervalMs: Long = 10, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met within ${timeoutMs}ms" }
            Thread.sleep(intervalMs)
        }
    }
}
