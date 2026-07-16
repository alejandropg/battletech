package battletech.tui

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.RangeBand
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.BattleSession
import battletech.tactical.session.MatchEnded
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.TurnEnded
import battletech.tactical.unit.UnitId
import battletech.tui.game.AppState
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.hex.sessionNoticeIcon
import battletech.tui.loop.UiEvent
import battletech.tui.loop.runLoop
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.AttackResultsView
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class TuiAppLoopTest {

    // TRUECOLOR so the renderer emits ANSI escape sequences we can search for.
    // Width/height must be positive to pass TuiApp.currentSize() checks.
    private val recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 120, height = 40)
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = recorder)
    private val renderer = ScreenRenderer(terminal)

    /**
     * Build a minimal AppState backed by a real BattleSession positioned at
     * MOVEMENT phase with PLAYER_1 as active mover and a PLAYER_2 unit at (0,0).
     * Pressing Enter on that hex yields FlashMessage("Not your unit").
     */
    private fun buildAppState(): AppState {
        val p2Unit = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(0, 0),
        )
        val p1Unit = aUnit(
            id = "ally",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(1, 1),
            walkingMP = 3,
            runningMP = 5,
        )
        val gameState = aGameState(
            units = listOf(p1Unit, p2Unit),
            map = aGameMap(cols = 5, rows = 5),
        )
        val turnState = aTurnState()
        return AppState(
            gameState = gameState,
            turnState = turnState,
            phase = MovementPhase.SelectingUnit,
            cursor = HexCoordinates(0, 0),
        )
    }

    // -------------------------------------------------------------------------
    // Test 1: scripted Input + Quit flow completes and produces multiple renders
    //
    // runLoop is called directly (not via launch) so no dispatcher scheduling needed.
    // Quit is in the scripted flow so takeWhile terminates and the loop exits.
    // -------------------------------------------------------------------------

    @Test
    fun `scripted Input and Quit flow completes and produces at least two rendered frames`() = runTest {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)
        // Quit is included in the scripted flow — takeWhile stops on Quit and
        // merge cancels all source coroutines so the loop exits cleanly.
        val events = flowOf<UiEvent>(
            UiEvent.Input(KeyboardEvent("ArrowUp")),
            UiEvent.Quit,
        )

        runLoop(
            events = merge(events, internalEvents.receiveAsFlow()),
            internalEvents = internalEvents,
            terminal = terminal,
            renderer = renderer,
            initialState = buildAppState(),
        )

        // Cursor-home sequence (ESC[1;1H) appears once per renderFrame call.
        // We expect at least: initial frame + frame after Input.
        val out = recorder.output()
        assertTrue(out.isNotEmpty(), "Expected non-empty recorder output")
        val cursorHomeCount = out.countOccurrences("[1;1H")
        assertTrue(cursorHomeCount >= 2, "Expected at least 2 render passes, got $cursorHomeCount cursor-home sequences")
    }

    // -------------------------------------------------------------------------
    // Test 2: Flash lifecycle — appears on trigger, disappears after expiry
    //
    // UnconfinedTestDispatcher lets launched coroutines run eagerly so we can
    // drive the loop by sending to the channel and observing output immediately.
    // -------------------------------------------------------------------------

    @Test
    fun `flash appears after Not-your-unit trigger`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        // Launch runLoop: with UnconfinedTestDispatcher it starts running eagerly
        // and suspends when it hits the first channel receive (empty channel).
        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
            )
        }

        // Initial frame is rendered (loop is now suspended waiting for events).
        // Enter on PLAYER_2 unit at (0,0) while PLAYER_1 is active → "Not your unit" flash.
        internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))
        // With UnconfinedTestDispatcher, send() resumes the loop eagerly; by the time
        // send() returns (or shortly after), the event has been processed and flash rendered.

        val outWithFlash = recorder.output()
        assertTrue(
            outWithFlash.contains("Not your unit"),
            "Expected 'Not your unit' flash in output after pressing Enter on enemy unit",
        )

        // Stop the loop.
        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    @Test
    fun `flash text clears after FlashExpired fires through virtual time`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
            )
        }

        // Trigger the flash.
        internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))
        assertTrue(recorder.output().contains("Not your unit"), "Flash should appear after trigger")

        // Advance virtual time past the 3s flash duration — the flash job's delay() fires,
        // sending FlashExpired back through internalEvents. The loop processes it and re-renders
        // without the flash text.
        recorder.clearOutput()
        advanceTimeBy(3100.milliseconds)

        val outAfterExpiry = recorder.output()
        assertFalse(
            outAfterExpiry.contains("Not your unit"),
            "Flash text should be gone after FlashExpired, but still present",
        )

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 3: Resized event triggers a re-render
    // -------------------------------------------------------------------------

    @Test
    fun `Resized event triggers re-render`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
            )
        }

        // Initial frame rendered, loop suspended on receive.
        val outputBefore = recorder.output().length
        assertTrue(outputBefore > 0, "Expected initial frame to be rendered")

        // Send a resize event — loop processes it and re-renders.
        internalEvents.send(UiEvent.Resized(Size(160, 45)))

        val outputAfter = recorder.output().length
        assertTrue(outputAfter > outputBefore, "Expected more output after Resized event triggered re-render")

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 4: Stale FlashExpired is ignored; newer flash remains visible
    // -------------------------------------------------------------------------

    @Test
    fun `stale FlashExpired generation is ignored, newer flash stays visible`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
            )
        }

        // Flash A: generation 1.
        internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))
        assertTrue(recorder.output().contains("Not your unit"), "Flash A should appear")

        // Flash B: generation 2 replaces flash A (same message text, different generation).
        internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))

        // Deliver stale FlashExpired for generation 1 — must NOT clear generation 2 flash.
        // A stale expiry does not trigger a re-render. Send a subsequent Resized event to
        // force a render so we can inspect the frame with flash B still active.
        recorder.clearOutput()
        internalEvents.send(UiEvent.FlashExpired(1L))
        internalEvents.send(UiEvent.Resized(Size(120, 40)))

        assertTrue(
            recorder.output().contains("Not your unit"),
            "Flash B should still be visible after stale FlashExpired(1) — active generation is 2",
        )

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 5: an exception while handling one event must not kill the loop.
    //
    // This guards the Ctrl+C-freeze fix: if event handling ever throws (e.g. a
    // rendering bug), the exception used to propagate out of collect{} and cancel
    // the whole coroutineScope — including the terminal input producer, which could
    // leave it stuck mid-blocking-read in raw mode. The loop must instead log and
    // keep collecting, so a subsequent Quit (e.g. ctrl+c) is still honored.
    //
    // The per-event guard in TuiApp.runLoop catches `Throwable`, not just `Exception`,
    // because a jar rewritten under a live JVM (e.g. redeploy-while-running) can surface
    // NoClassDefFoundError/LinkageError — both Errors, not Exceptions — while handling a
    // single event. There is no clean way to provoke a real java.lang.Error through this
    // test's public event surface (AppState/phase handling has no reachable Error path,
    // and faking one would require test-only production hooks, which we avoid per the
    // project's no-gold-plating stance). The NegativeArraySizeException below stands in
    // as a representative "something throws mid-handling" case; it exercises the same
    // catch-and-continue path that now also covers Throwable.
    // -------------------------------------------------------------------------

    @Test
    fun `exception while handling one event does not stop the loop from reaching Quit`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
            )
        }

        // Negative dimensions make ScreenBuffer's backing array allocation throw
        // NegativeArraySizeException inside renderFrame — a stand-in for any bug
        // surfacing during event handling.
        internalEvents.send(UiEvent.Resized(Size(-1, 40)))

        // The loop must still be alive and processing events after the failure.
        recorder.clearOutput()
        internalEvents.send(UiEvent.Resized(Size(120, 40)))
        assertTrue(recorder.output().isNotEmpty(), "Expected loop to keep rendering after a handled exception")

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
        assertTrue(loopJob.isCompleted, "Expected loop to terminate cleanly on Quit after recovering from an exception")
    }

    // -------------------------------------------------------------------------
    // Test 6: MatchEnded event causes game-over banner to appear in the render
    // -------------------------------------------------------------------------

    @Test
    fun `game-over banner renders when MatchEnded event is received with a winner`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
            )
        }

        internalEvents.send(UiEvent.Session(MatchEnded(MatchOutcome.Victory(PlayerId.PLAYER_1))))

        val output = recorder.output()
        assertTrue(output.contains("MATCH OVER"), "Expected 'MATCH OVER' banner title in output")
        assertTrue(output.contains("P1 wins!"), "Expected winner label 'P1 wins!' in output")

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    @Test
    fun `game-over banner shows Draw when MatchEnded has a Draw outcome`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
            )
        }

        internalEvents.send(UiEvent.Session(MatchEnded(MatchOutcome.Draw)))

        val output = recorder.output()
        assertTrue(output.contains("MATCH OVER"), "Expected 'MATCH OVER' banner title in output")
        assertTrue(output.contains("Draw"), "Expected 'Draw' in output for null winner")

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 7: Input is blocked after match ends — no phase transitions fire
    // -------------------------------------------------------------------------

    @Test
    fun `game input is blocked after MatchEnded and no phase transitions fire`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
            )
        }

        // End the match, then clear recorded output to isolate subsequent renders.
        internalEvents.send(UiEvent.Session(MatchEnded(MatchOutcome.Victory(PlayerId.PLAYER_1))))
        recorder.clearOutput()

        // Pressing Enter on the enemy unit at (0,0) would normally produce the
        // "Not your unit" flash, but input must be blocked after match ends.
        internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))

        assertFalse(
            recorder.output().contains("Not your unit"),
            "Phase input should be blocked after MatchEnded — 'Not your unit' flash must not appear",
        )

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 8: passive side (remote play) — Session(AttacksResolved) must
    // populate lastAttackResults just like the submitter's own Transition
    // does, so the ATTACK RESULTS panel appears on both terminals.
    // -------------------------------------------------------------------------

    @Test
    fun `Session AttacksResolved populates lastAttackResults so the results panel appears`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
            )
        }

        recorder.clearOutput()
        internalEvents.send(UiEvent.Session(AttacksResolved(listOf(aResult()))))

        assertTrue(
            recorder.output().contains(AttackResultsView.TITLE),
            "Expected ATTACK RESULTS panel to appear after a passive-side AttacksResolved event",
        )

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 9: resync into WEAPON_ATTACK clears a pre-set lastAttackResults —
    // mirrors commitAttackImpulse's isNewWeaponAttackPhase -> null so a new
    // turn's weapon phase starts clean on the passive side too.
    // -------------------------------------------------------------------------

    @Test
    fun `resync into WEAPON_ATTACK clears a pre-set lastAttackResults`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        // Session is constructed already sitting at WEAPON_ATTACK while the TUI
        // phase below is MovementPhase — a deliberate mismatch so the first
        // Session event triggers the resync branch. Includes a unit matching
        // aResult()'s attackerId so AttackResultsView's owner lookup resolves.
        val base = AppState(
            gameState = aGameState(units = listOf(aUnit(id = "ally", owner = PlayerId.PLAYER_1))),
            turnState = aTurnState(),
            phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
            cursor = HexCoordinates(0, 0),
        )
        val initialState = base.copy(
            phase = MovementPhase.SelectingUnit,
            lastAttackResults = listOf(aResult()),
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

        assertTrue(
            recorder.output().contains(AttackResultsView.TITLE),
            "Precondition: results panel should be visible before the resync",
        )
        recorder.clearOutput()

        internalEvents.send(UiEvent.Session(TurnEnded(1)))

        assertFalse(
            recorder.output().contains(AttackResultsView.TITLE),
            "Resync into WEAPON_ATTACK must clear lastAttackResults so the panel disappears",
        )

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 10: hot-seat regression — when the TUI phase already matches the
    // session phase (no resync), a Session event must NOT clear a pre-set
    // lastAttackResults.
    // -------------------------------------------------------------------------

    @Test
    fun `Session event does not clear lastAttackResults when phase already matches (no resync)`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)
        val initialState = buildAppState().copy(lastAttackResults = listOf(aResult()))

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = initialState,
            )
        }

        recorder.clearOutput()
        internalEvents.send(UiEvent.Session(TurnEnded(1)))

        assertTrue(
            recorder.output().contains(AttackResultsView.TITLE),
            "No-resync Session event must not clear a pre-set lastAttackResults",
        )

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 11: SessionNotice is just another gameLog entry now (the old
    // parallel UI-notice mechanism is gone) — it renders in the LOG panel
    // with its lan-connect icon at its true chronological log position. Mirrors production: a
    // BattleSession.annotate call appends to the log and dispatches through
    // subscribers, which the TUI turns into UiEvent.Session for a re-render.
    // -------------------------------------------------------------------------

    @Test
    fun `SessionNotice recorded in the gameLog renders in the LOG panel with the lan-connect icon`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)
        val initialState = buildAppState()
        val session = initialState.anySession as BattleSession

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = initialState,
            )
        }

        recorder.clearOutput()
        val notice = SessionNotice("Opponent connected")
        session.annotate(notice)
        internalEvents.send(UiEvent.Session(notice))

        assertTrue(
            recorder.output().contains("${sessionNoticeIcon()} Opponent connected"),
            "Expected the SessionNotice to render in the LOG panel at its log position with the lan-connect icon",
        )

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // ---- helpers ----

    // attackerId defaults to "ally" (buildAppState()'s PLAYER_1 unit) since AttackResultsView
    // now looks the attacker up in the rendered gameState's unitOwners via getValue (fails loud
    // on an unknown id, rather than silently rendering white as the old nullable playerColor did).
    private fun aResult(attackerId: UnitId = UnitId("ally")) = AttackResult.Miss(
        attackerId = attackerId,
        targetId = UnitId("b"),
        weaponName = "Med Laser",
        targetNumber = 7,
        toHitRoll = DiceRoll(2, 3),
        gunnery = 4,
        rangeBand = RangeBand.SHORT,
    )

    private fun String.countOccurrences(sub: String): Int {
        if (sub.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            idx = this.indexOf(sub, idx)
            if (idx == -1) break
            count++
            idx += sub.length
        }
        return count
    }
}
