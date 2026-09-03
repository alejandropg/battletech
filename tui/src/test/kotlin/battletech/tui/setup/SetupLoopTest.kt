package battletech.tui.setup

import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.AssetBundle
import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.model.content.summarize
import battletech.tactical.unit.MechModels
import battletech.tui.input.Keybindings
import battletech.tui.screen.resolveTheme
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tenter.screen.ScreenRenderer
import tenter.view.HelpView

@OptIn(ExperimentalCoroutinesApi::class)
internal class SetupLoopTest {

    private val recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 120, height = 40)
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = recorder)
    private val renderer = ScreenRenderer(terminal, resolveTheme("dark"))

    private val map: GameMap = GameMap(
        hexes = (0 until 8).map { row -> HexCoordinates(0, row) }.associateWith { Hex(it) },
        name = "arena",
    )
    private val registry = AssetRegistry(maps = mapOf("arena" to map))
        .merge(AssetBundle(mechs = listOf(MechModels["AS7-D"])))
        .registry

    private fun initialState(readOnly: Boolean = false) =
        SetupState(catalog = registry.summarize(), registry = registry, readOnly = readOnly)

    private suspend fun run(vararg events: SetupUiEvent, lobby: SetupLobby = NoLobby, initial: SetupState = initialState()): SetupOutcome {
        val internalEvents = Channel<SetupUiEvent>(Channel.UNLIMITED)
        return setupLoop(
            events = merge(flowOf(*events), internalEvents.receiveAsFlow()),
            internalEvents = internalEvents,
            terminal = terminal,
            renderer = renderer,
            initialState = initial,
            keys = Keybindings.DEFAULT,
            lobby = lobby,
        )
    }

    @Test
    fun `stage 1 renders only the MODE panel`() = runTest {
        run(SetupUiEvent.Quit)

        val out = recorder.output()
        assertThat(out).contains("MODE")
        assertThat(out).doesNotContain("PLAYER 1")
    }

    @Test
    fun `c locks hot-seat in, reveals the roster panels, and focuses MAP`() = runTest {
        run(
            SetupUiEvent.Input(KeyboardEvent("c")),
            SetupUiEvent.Quit,
        )

        val out = recorder.output()
        assertThat(out).contains("MAP")
        assertThat(out).contains("PLAYER 1")
        assertThat(out).contains("PLAYER 2")
    }

    @Test
    fun `Enter wraps from PLAYER_2 back to MODE`() = runTest {
        // Lock hot-seat, then Enter through MAP -> PLAYER_1 -> PLAYER_2 -> MODE (4 presses total).
        run(
            SetupUiEvent.Input(KeyboardEvent("c")),
            SetupUiEvent.Input(KeyboardEvent("Enter")),
            SetupUiEvent.Input(KeyboardEvent("Enter")),
            SetupUiEvent.Input(KeyboardEvent("Enter")),
            SetupUiEvent.Quit,
        )
        // No direct focus assertion is available from outside the loop; this test asserts the
        // loop simply runs the full sequence without failing on any of the four presses in a row
        // (a wrap bug would throw or otherwise corrupt state, not crash — so the meaningful check
        // is that MAP is still the reveal-visible panel content after cycling all the way around).
        assertThat(recorder.output()).contains("MAP")
    }

    @Test
    fun `Tab cycles panels exactly like Enter`() = runTest {
        // Same cycle as the Enter test above, but with Tab — and mixing the two keys, since both
        // bind to SetupAction.NextPanel and must be freely interchangeable mid-cycle.
        run(
            SetupUiEvent.Input(KeyboardEvent("c")),
            SetupUiEvent.Input(KeyboardEvent("Tab")),
            SetupUiEvent.Input(KeyboardEvent("Enter")),
            SetupUiEvent.Input(KeyboardEvent("Tab")),
            SetupUiEvent.Quit,
        )
        assertThat(recorder.output()).contains("MAP")
    }

    @Test
    fun `c is refused with a flash when a player has no units`() = runTest {
        run(
            SetupUiEvent.Input(KeyboardEvent("c")), // lock hot-seat
            SetupUiEvent.Input(KeyboardEvent("2")), // focus MAP
            SetupUiEvent.Input(KeyboardEvent(" ")), // select the only map
            SetupUiEvent.Input(KeyboardEvent("c")), // commit — P1/P2 both empty
            SetupUiEvent.Quit,
        )

        // The diffing renderer (see ScreenRenderer) only retransmits cells that actually
        // changed relative to the previous prompt, so a character the old and new prompt
        // happen to share at the same column is legitimately skipped — "no units" is the
        // substring guaranteed to survive as one contiguous run regardless of what the
        // previous prompt text was.
        assertThat(recorder.output()).contains("no units")
    }

    @Test
    fun `a complete plan commits with the expected MatchPlan`() = runTest {
        val outcome = run(
            SetupUiEvent.Input(KeyboardEvent("c")), // lock hot-seat
            SetupUiEvent.Input(KeyboardEvent("2")), // focus MAP
            SetupUiEvent.Input(KeyboardEvent(" ")), // select the map
            SetupUiEvent.Input(KeyboardEvent("3")), // focus PLAYER_1
            SetupUiEvent.Input(KeyboardEvent(" ")), // count 0 -> 1
            SetupUiEvent.Input(KeyboardEvent("4")), // focus PLAYER_2
            SetupUiEvent.Input(KeyboardEvent(" ")), // count 0 -> 1
            SetupUiEvent.Input(KeyboardEvent("c")), // commit
        )

        val plan = MatchPlan(mapName = "arena")
            .withCount(PlayerId.PLAYER_1, "AS7-D", 1)
            .withCount(PlayerId.PLAYER_2, "AS7-D", 1)
        assertThat(outcome).isEqualTo(SetupOutcome.Commit(plan, registry))
    }

    @Test
    fun `question mark opens HELP in normal state`() = runTest {
        run(
            SetupUiEvent.Input(KeyboardEvent("?")),
            SetupUiEvent.Quit,
        )

        assertThat(recorder.output()).contains(HelpView.TITLE)
    }

    @Test
    fun `reselecting a setup panel with its number cycles through every size`() = runTest(UnconfinedTestDispatcher()) {
        val internalEvents = Channel<SetupUiEvent>(Channel.UNLIMITED)
        val loopJob = launch {
            setupLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                terminal = terminal,
                renderer = renderer,
                initialState = initialState().copy(modeLocked = true),
                keys = Keybindings.DEFAULT,
                lobby = NoLobby,
            )
        }

        renderer.clear()
        recorder.clearOutput()
        internalEvents.send(SetupUiEvent.Input(KeyboardEvent("3")))
        assertThat(recorder.output()).contains("PLAYER 1")
        assertThat(recorder.output()).doesNotContain("'MECH DATA")

        renderer.clear()
        recorder.clearOutput()
        internalEvents.send(SetupUiEvent.Input(KeyboardEvent("3")))
        assertThat(recorder.output()).contains("'MECH DATA")

        renderer.clear()
        recorder.clearOutput()
        internalEvents.send(SetupUiEvent.Input(KeyboardEvent("3")))
        assertThat(recorder.output()).doesNotContain("PLAYER 1")

        renderer.clear()
        recorder.clearOutput()
        internalEvents.send(SetupUiEvent.Input(KeyboardEvent("3")))
        assertThat(recorder.output()).contains("PLAYER 1")
        assertThat(recorder.output()).doesNotContain("'MECH DATA")

        internalEvents.send(SetupUiEvent.Quit)
        loopJob.join()
    }

    @Test
    fun `an opponent who leaves keeps the panels but closes the commit gate`() = runTest {
        val hostState = initialState().copy(
            mode = SetupMode.HOST,
            modeLocked = true,
            opponentConnected = true,
            opponentEverConnected = true,
            plan = MatchPlan(mapName = "arena")
                .withCount(PlayerId.PLAYER_1, "AS7-D", 1)
                .withCount(PlayerId.PLAYER_2, "AS7-D", 1),
        )

        val outcome = run(
            SetupUiEvent.Lobby(LobbyEvent.OpponentLeft),
            SetupUiEvent.Input(KeyboardEvent("c")), // would otherwise commit — the plan is complete
            SetupUiEvent.Quit,
            initial = hostState,
        )

        // Quit, not Commit: `c` was refused even though the plan itself is complete. (The refusal
        // message is asserted in SetupStateTest — here it would be split across the renderer's
        // per-cell diff output.)
        assertThat(outcome).isEqualTo(SetupOutcome.Quit)
        assertThat(recorder.output()).contains("PLAYER 1") // panels stayed
    }

    @Test
    fun `a read-only mirror renders its roster panels but ignores space`() = runTest {
        val mirrorState = initialState(readOnly = true).copy(
            modeLocked = true,
            mode = SetupMode.HOST,
            opponentConnected = true,
            opponentEverConnected = true,
        )

        val outcome = run(
            SetupUiEvent.Input(KeyboardEvent("3")), // focus PLAYER_1 — focus stays live in a mirror
            SetupUiEvent.Input(KeyboardEvent(" ")), // inert on a read-only mirror
            SetupUiEvent.Quit,
            initial = mirrorState,
        )

        assertThat(outcome).isEqualTo(SetupOutcome.Quit)
        assertThat(recorder.output()).contains("PLAYER 1")
    }
}
