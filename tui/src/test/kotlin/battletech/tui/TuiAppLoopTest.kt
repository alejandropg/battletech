package battletech.tui

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.ToHitAttempt
import battletech.tactical.attack.ToHitBase
import battletech.tactical.attack.ToHitBreakdown
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
import battletech.tui.game.phase.BOARD_ORIGIN_X
import battletech.tui.game.phase.BOARD_ORIGIN_Y
import battletech.tui.game.phase.MovementPhase
import battletech.tui.icon.sessionNoticeIcon
import battletech.tui.input.Keybindings
import battletech.tui.loop.UiEvent
import battletech.tui.loop.runLoop
import battletech.tui.screen.resolveTheme
import battletech.tui.view.AttackResultsView
import battletech.tui.view.LogView
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
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
import tenter.screen.ScreenRenderer
import tenter.view.HelpView
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class TuiAppLoopTest {

    // TRUECOLOR so the renderer emits ANSI escape sequences we can search for.
    // Width/height must be positive to pass TuiApp.currentSize() checks.
    private val recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 120, height = 40)
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = recorder)
    private val renderer = ScreenRenderer(terminal, resolveTheme("dark"))

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
        // ArrowDown, not ArrowUp: the cursor starts at (0,0) on a 5x5 map, and moveCursor()
        // clamps to the current hex when the neighbor is off the map — north from row 0 is off
        // the map, so ArrowUp would leave the cursor (and the render) unchanged.
        val events = flowOf(
            UiEvent.Input(KeyboardEvent("ArrowDown")),
            UiEvent.Quit,
        )

        runLoop(
            events = merge(events, internalEvents.receiveAsFlow()),
            internalEvents = internalEvents,
            terminal = terminal,
            renderer = renderer,
            initialState = buildAppState(),
            keys = Keybindings.DEFAULT,
        )

        // The diffing renderer (see ScreenRenderer) only sends a full cursor-home + repaint on
        // the very first frame; a later frame that only moved the cursor sends setPosition(s)
        // just for the cells that changed, not another [1;1H. So "at least 2 render passes" is
        // checked as at least 2 distinct position writes overall, not 2 cursor-home sequences —
        // this generalizes to both the full-repaint and the dirty-cell-diff render paths.
        val out = recorder.output()
        assertTrue(out.isNotEmpty(), "Expected non-empty recorder output")
        val positionWrites = Regex("\\[\\d+;\\d+H").findAll(out).count()
        assertTrue(positionWrites >= 2, "Expected at least 2 position writes (2 render passes), got $positionWrites")
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
                keys = Keybindings.DEFAULT,
            )
        }

        // Initial frame is rendered (loop is now suspended waiting for events).
        // Enter on PLAYER_2 unit at (0,0) while PLAYER_1 is active → "Not your unit" flash.
        internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))
        // With UnconfinedTestDispatcher, send() resumes the loop eagerly; by the time
        // send() returns (or shortly after), the event has been processed and flash rendered.

        val outWithFlash = recorder.output()
        assertTrue(
            outWithFlash.contains("Not your"),
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
                keys = Keybindings.DEFAULT,
            )
        }

        // Trigger the flash.
        internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))
        assertTrue(recorder.output().contains("Not your"), "Flash should appear after trigger")

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
                keys = Keybindings.DEFAULT,
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
                keys = Keybindings.DEFAULT,
            )
        }

        // Flash A: generation 1.
        internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))
        // The diff renderer only emits changed cells. The previous prompt and flash share the
        // trailing " unit", so the observable changed run is "Not your".
        assertTrue(recorder.output().contains("Not your"), "Flash A should appear")

        // Flash B: generation 2 replaces flash A (same trigger, same message text — only the
        // internal generation counter differs, which isn't independently visible on screen).
        internalEvents.send(UiEvent.Input(KeyboardEvent("Enter")))

        // Deliver stale FlashExpired for generation 1 — must NOT clear generation 2 flash. A
        // stale expiry doesn't trigger a re-render at all (see runLoop's FlashExpired branch),
        // so under the diffing renderer (see ScreenRenderer) the correct, directly-observable
        // signal that generation 2 survived untouched is that nothing gets sent: if it HAD been
        // wrongly cleared, the prompt reverting away from "Not your unit" is a real visual change
        // and would show up as non-empty output.
        recorder.clearOutput()
        internalEvents.send(UiEvent.FlashExpired(1L))

        assertTrue(
            recorder.output().isEmpty(),
            "A stale FlashExpired must not trigger a render, let alone clear flash B",
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
                keys = Keybindings.DEFAULT,
            )
        }

        // Negative dimensions make ScreenBuffer's backing array allocation throw
        // NegativeArraySizeException inside renderFrame — a stand-in for any bug
        // surfacing during event handling.
        internalEvents.send(UiEvent.Resized(Size(-1, 40)))

        // The loop must still be alive and processing events after the failure. Resized to a
        // size distinct from the terminal's original 120x40 (not back to 120x40): the diffing
        // renderer (see ScreenRenderer) would otherwise diff this recovery frame against the
        // last successful one — content-identical, since nothing about appState actually
        // changed — and correctly send nothing, which would look indistinguishable from the
        // loop being dead. A genuinely new size forces a full repaint, so non-empty output here
        // specifically proves the loop recovered and rendered again.
        recorder.clearOutput()
        internalEvents.send(UiEvent.Resized(Size(130, 42)))
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
                keys = Keybindings.DEFAULT,
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
                keys = Keybindings.DEFAULT,
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
    // Test 7: a board click reaches the phase.
    //
    // The match-ended input block that used to be tested here now lives in
    // RunLoopInputResolutionTest: once the match is over, Workspace.render swaps
    // the status bar for the match-over line and stops drawing flash text at all,
    // so a blocked input and a handled one render identically and any assertion
    // on rendered output passes whether or not the block exists.
    //
    // What IS worth asserting end-to-end is the positive: that a click composes
    // all the way through — mouse-scroll interception, hit-testing against the
    // real frame's board origin, BoardClick, and the phase's own handling.
    // -------------------------------------------------------------------------

    @Test
    fun `a board click on the enemy unit reaches the phase and flashes`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
                keys = Keybindings.DEFAULT,
            )
        }

        // Deliberately no clearOutput(): the diffing renderer rewrites only changed cells, and a
        // style run can split the flash text with escape sequences mid-string, so this matches on
        // the same "Not your" prefix the Enter-flash test above uses.
        internalEvents.send(UiEvent.Input(MouseEvent(x = BOARD_ORIGIN_X, y = BOARD_ORIGIN_Y, left = true)))

        assertTrue(
            recorder.output().contains("Not your"),
            "A left click on hex (0,0) must resolve to a BoardClick and reach the phase",
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
                keys = Keybindings.DEFAULT,
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
                keys = Keybindings.DEFAULT,
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
                keys = Keybindings.DEFAULT,
            )
        }

        recorder.clearOutput()
        internalEvents.send(UiEvent.Session(TurnEnded(1)))

        // Nothing in appState actually changes for a no-resync Session event (phase, matchEnded,
        // and lastAttackResults all fall through to their previous values), so the diffing
        // renderer (see ScreenRenderer) sends nothing at all. That emptiness is a stronger,
        // more direct proof that lastAttackResults specifically was untouched than re-scanning
        // for the panel title would be: if it HAD been cleared, the panel disappearing is a real
        // visual change and would show up as non-empty output.
        assertTrue(
            recorder.output().isEmpty(),
            "No-resync Session event must not clear a pre-set lastAttackResults, and since nothing " +
                "else changed, must not repaint anything",
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
                keys = Keybindings.DEFAULT,
            )
        }

        recorder.clearOutput()
        val notice = SessionNotice("Opponent connected")
        session.annotate(notice)
        internalEvents.send(UiEvent.Session(notice))

        // Checked as separate tokens, not one joined phrase: the diffing renderer (see
        // ScreenRenderer) only retransmits cells that actually changed, so a separator space
        // that was already blank in the previous frame is legitimately skipped -- the terminal
        // still displays "Opponent connected" correctly, but the raw byte log the recorder
        // captures may show the words as disjoint writes rather than one contiguous run.
        val out = recorder.output()
        assertTrue(out.contains(sessionNoticeIcon()), "Expected the lan-connect icon in the LOG panel")
        assertTrue(out.contains("Opponent"), "Expected the SessionNotice text in the LOG panel")
        assertTrue(out.contains("connected"), "Expected the SessionNotice text in the LOG panel")

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 12: ? opens/closes the HELP panel (and focuses it while open);
    // 9 focuses LOG so `-` can minimize it to its stub; arrows belong to
    // the focused panel, not the phase; ? keeps working once the match has
    // ended.
    // -------------------------------------------------------------------------

    @Test
    fun `question mark opens and closes the HELP panel`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
                keys = Keybindings.DEFAULT,
            )
        }

        assertFalse(recorder.output().contains(HelpView.TITLE), "HELP should be closed by default")

        recorder.clearOutput()
        internalEvents.send(UiEvent.Input(KeyboardEvent("?")))
        assertTrue(recorder.output().contains(HelpView.TITLE), "? should open HELP")

        recorder.clearOutput()
        internalEvents.send(UiEvent.Input(KeyboardEvent("?")))
        assertFalse(recorder.output().contains(HelpView.TITLE), "? again should close HELP")

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    @Test
    fun `9 focuses LOG, then minus minimizes it to its stub`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
                keys = Keybindings.DEFAULT,
            )
        }

        assertTrue(recorder.output().contains(LogView.TITLE), "LOG should render at NORMAL initially")

        internalEvents.send(UiEvent.Input(KeyboardEvent("9"))) // focus LOG (badge '9')

        recorder.clearOutput()
        internalEvents.send(UiEvent.Input(KeyboardEvent("-"))) // NORMAL -> MINIMIZED

        // A minimized panel draws its title one character per row, so the horizontal
        // "LOG" string no longer appears anywhere in the frame — it becomes a stub.
        assertFalse(recorder.output().contains(LogView.TITLE), "'-' should minimize the focused LOG panel to a stub")

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    @Test
    fun `question mark still opens HELP after the match ends`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

        val loopJob = launch {
            runLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = buildAppState(),
                keys = Keybindings.DEFAULT,
            )
        }

        internalEvents.send(UiEvent.Session(MatchEnded(MatchOutcome.Victory(PlayerId.PLAYER_1))))
        recorder.clearOutput()

        internalEvents.send(UiEvent.Input(KeyboardEvent("?")))

        assertTrue(recorder.output().contains(HelpView.TITLE), "? should still open HELP after MatchEnded")

        internalEvents.send(UiEvent.Quit)
        loopJob.join()
    }

    // -------------------------------------------------------------------------
    // Test 13: a manual board pan must survive subsequent renders. Auto-follow
    // fires on reveal-target MOVEMENT, not on every render — otherwise the board
    // springs back to the cursor on the next event of any kind, undoing the pan.
    // -------------------------------------------------------------------------

    /**
     * A map far wider than the 120-column test terminal, so the board genuinely scrolls.
     *
     * The "QQ" marker unit deliberately sits AWAY from the cursor: the cursor-driven panels
     * (UNIT_STATUS / TARGET_STATUS) render the id of whatever unit is under the cursor, which
     * would make "QQ" appear in the frame for reasons that have nothing to do with the board's
     * scroll position. Off the cursor, "QQ" appears only as a board glyph.
     */
    private fun buildWideMapAppState(): AppState {
        val p2Unit = aUnit(id = "QQ", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 2))
        val p1Unit = aUnit(
            id = "ally",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(1, 1),
            walkingMP = 3,
            runningMP = 5,
        )
        return AppState(
            gameState = aGameState(units = listOf(p1Unit, p2Unit), map = aGameMap(cols = 40, rows = 20)),
            turnState = aTurnState(),
            phase = MovementPhase.SelectingUnit,
            cursor = HexCoordinates(0, 0),
        )
    }

    @Test
    fun `a board pan survives an unrelated re-render, and cursor movement re-engages follow`() =
        runTest(UnconfinedTestDispatcher()) {
            val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

            val loopJob = launch {
                runLoop(
                    events = internalEvents.receiveAsFlow(),
                    internalEvents = internalEvents,
                    terminal = terminal,
                    renderer = renderer,
                    initialState = buildWideMapAppState(),
                    keys = Keybindings.DEFAULT,
                )
            }

            // The marker unit at (2,2) is near the left edge, so it is on screen at rest.
            assertTrue(recorder.output().contains("QQ"), "marker unit should be visible before panning")

            // Pan right far enough to push the marker off the left edge. The recorder accumulates
            // every frame, so the output is cleared before the LAST press and asserted on that
            // frame alone — by then the marker has long since scrolled away, and a pan redraws
            // the whole board area, so it would be retransmitted if it were still on screen.
            repeat(19) { internalEvents.send(UiEvent.Input(KeyboardEvent("l"))) }
            recorder.clearOutput()
            internalEvents.send(UiEvent.Input(KeyboardEvent("l")))
            assertFalse(
                recorder.output().contains("QQ"),
                "panning right should scroll the marker unit out of view",
            )

            // An unrelated event re-renders. If auto-follow ran unconditionally it would drag the
            // board back to the cursor and redraw the unit — the diffing renderer emits exactly
            // the cells that changed, so its reappearance would show up here.
            recorder.clearOutput()
            internalEvents.send(UiEvent.Input(KeyboardEvent("?")))
            assertFalse(
                recorder.output().contains("QQ"),
                "an unrelated re-render must not snap the board back to the cursor",
            )

            // ? opened AND focused HELP — while it's focused, arrow keys scroll HELP rather
            // than reaching the phase (see RunLoop's dispatch order). Close it again so focus
            // returns to the board and ArrowDown reaches the phase's cursor movement below.
            internalEvents.send(UiEvent.Input(KeyboardEvent("?")))

            // Moving the cursor is a reveal-target change, so follow re-engages and brings it back.
            recorder.clearOutput()
            internalEvents.send(UiEvent.Input(KeyboardEvent("ArrowDown")))
            assertTrue(
                recorder.output().contains("QQ"),
                "cursor movement should follow the board back to the cursor",
            )

            internalEvents.send(UiEvent.Quit)
            loopJob.join()
        }

    // -------------------------------------------------------------------------
    // Test 14: arrows belong to the FOCUSED panel. While a side panel holds
    // focus they scroll it and never reach the phase; once the board is focused
    // again the same key moves the cursor as it always did.
    // -------------------------------------------------------------------------

    @Test
    fun `arrows scroll the focused side panel instead of moving the cursor, and reach the phase again once the board is focused`() =
        runTest(UnconfinedTestDispatcher()) {
            val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)

            val loopJob = launch {
                runLoop(
                    events = internalEvents.receiveAsFlow(),
                    internalEvents = internalEvents,
                    terminal = terminal,
                    renderer = renderer,
                    initialState = buildWideMapAppState(),
                    keys = Keybindings.DEFAULT,
                )
            }

            // Same idiom as the pan test above: push the marker unit off the left edge, so any
            // cursor movement (which re-engages auto-follow) would visibly drag it back.
            repeat(20) { internalEvents.send(UiEvent.Input(KeyboardEvent("l"))) }

            // Focus LOG. ArrowDown now scrolls LOG; if it still reached the phase the cursor would
            // move, follow would re-engage, and the marker would be redrawn.
            internalEvents.send(UiEvent.Input(KeyboardEvent("9")))
            recorder.clearOutput()
            internalEvents.send(UiEvent.Input(KeyboardEvent("ArrowDown")))
            assertFalse(
                recorder.output().contains("QQ"),
                "ArrowDown must not reach the phase while a side panel is focused",
            )

            // "0" focuses the board again, and the very same key moves the cursor once more.
            internalEvents.send(UiEvent.Input(KeyboardEvent("0")))
            recorder.clearOutput()
            internalEvents.send(UiEvent.Input(KeyboardEvent("ArrowDown")))
            assertTrue(
                recorder.output().contains("QQ"),
                "ArrowDown must reach the phase once the board is focused again",
            )

            internalEvents.send(UiEvent.Quit)
            loopJob.join()
        }

    // ---- helpers ----

    // attackerId defaults to "ally" (buildAppState()'s PLAYER_1 unit) since AttackResultsView
    // now looks the attacker up in the rendered gameState's unitOwners via getValue (fails loud
    // on an unknown id, rather than silently rendering white as the old nullable playerColor did).
    private fun aResult(attackerId: UnitId = UnitId("ally")) = AttackResult.Miss(
        attempt = ToHitAttempt(
            attackerId = attackerId,
            targetId = UnitId("b"),
            weaponName = "Med Laser",
            toHitRoll = DiceRoll(2, 3),
            toHit = ToHitBreakdown(ToHitBase.GUNNERY, skill = 4, modifiers = emptyList()),
        ),
    )
}
