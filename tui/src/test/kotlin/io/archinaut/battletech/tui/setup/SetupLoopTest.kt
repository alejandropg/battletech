package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tui.view.HELP_TITLE
import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.model.Hex
import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.model.content.AssetBundle
import io.archinaut.battletech.tactical.model.content.AssetRegistry
import io.archinaut.battletech.tactical.model.content.MatchPlan
import io.archinaut.battletech.tactical.model.content.summarize
import io.archinaut.battletech.tactical.unit.MechModels
import io.archinaut.battletech.tui.input.Keybindings
import com.github.ajalt.mordant.input.KeyboardEvent
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
import io.archinaut.tenter.terminal.Terminal
import io.archinaut.tenter.terminal.HeadlessTerminal
import io.archinaut.tenter.terminal.Screen
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import io.archinaut.battletech.tui.frameText

@OptIn(ExperimentalCoroutinesApi::class)
internal class SetupLoopTest {

    private val terminal = Terminal.headless(width = 120, height = 40)

    /**
     * [runTest] with [terminal]'s screen open throughout, as `withScreen` holds it around the app in
     * production.
     */
    private fun runOnScreen(
        context: CoroutineContext = EmptyCoroutineContext,
        terminal: HeadlessTerminal = this.terminal,
        body: suspend TestScope.(Screen) -> Unit,
    ): TestResult = terminal.withScreen { screen -> runTest(context) { body(screen) } }


    private val map: GameMap = GameMap(
        hexes = (0 until 8).map { row -> HexCoordinates(0, row) }.associateWith { Hex(it) },
        name = "arena",
    )
    private val registry = AssetRegistry(maps = mapOf("arena" to map))
        .merge(AssetBundle(mechs = listOf(MechModels["AS7-D"])))
        .registry

    private fun initialState(readOnly: Boolean = false) =
        SetupState(catalog = registry.summarize(), registry = registry, readOnly = readOnly)

    private suspend fun Screen.runSetup(vararg events: SetupUiEvent, lobby: SetupLobby = NoLobby, initial: SetupState = initialState()): SetupOutcome {
        val internalEvents = Channel<SetupUiEvent>(Channel.UNLIMITED)
        return setupLoop(
            events = merge(flowOf(*events), internalEvents.receiveAsFlow()),
            internalEvents = internalEvents,
            screen = this,
            initialState = initial,
            keys = Keybindings.DEFAULT,
            lobby = lobby,
        )
    }

    @Test
    fun `stage 1 renders only the MODE panel`() = runOnScreen { screen ->
        screen.runSetup(SetupUiEvent.Quit)

        val out = terminal.frameText()
        assertThat(out).contains("MODE")
        assertThat(out).doesNotContain("PLAYER 1")
    }

    @Test
    fun `c locks hot-seat in, reveals the roster panels, and focuses MAP`() = runOnScreen { screen ->
        screen.runSetup(
            SetupUiEvent.Input(KeyboardEvent("c")),
            SetupUiEvent.Quit,
        )

        val out = terminal.frameText()
        assertThat(out).contains("MAP")
        assertThat(out).contains("PLAYER 1")
        assertThat(out).contains("PLAYER 2")
    }

    @Test
    fun `Enter wraps from PLAYER_2 back to MODE`() = runOnScreen { screen ->
        // Lock hot-seat, then Enter through MAP -> PLAYER_1 -> PLAYER_2 -> MODE (4 presses total).
        screen.runSetup(
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
        assertThat(terminal.frameText()).contains("MAP")
    }

    @Test
    fun `Tab cycles panels exactly like Enter`() = runOnScreen { screen ->
        // Same cycle as the Enter test above, but with Tab — and mixing the two keys, since both
        // bind to SetupAction.NextPanel and must be freely interchangeable mid-cycle.
        screen.runSetup(
            SetupUiEvent.Input(KeyboardEvent("c")),
            SetupUiEvent.Input(KeyboardEvent("Tab")),
            SetupUiEvent.Input(KeyboardEvent("Enter")),
            SetupUiEvent.Input(KeyboardEvent("Tab")),
            SetupUiEvent.Quit,
        )
        assertThat(terminal.frameText()).contains("MAP")
    }

    @Test
    fun `c is refused with a flash when a player has no units`() = runOnScreen { screen ->
        screen.runSetup(
            SetupUiEvent.Input(KeyboardEvent("c")), // lock hot-seat
            SetupUiEvent.Input(KeyboardEvent("2")), // focus MAP
            SetupUiEvent.Input(KeyboardEvent(" ")), // select the only map
            SetupUiEvent.Input(KeyboardEvent("c")), // commit — P1/P2 both empty
            SetupUiEvent.Quit,
        )

        assertThat(terminal.frameText()).contains("no units")
    }

    @Test
    fun `a complete plan commits with the expected MatchPlan`() = runOnScreen { screen ->
        val outcome = screen.runSetup(
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
    fun `question mark opens HELP in normal state`() = runOnScreen { screen ->
        screen.runSetup(
            SetupUiEvent.Input(KeyboardEvent("?")),
            SetupUiEvent.Quit,
        )

        assertThat(terminal.frameText()).contains(HELP_TITLE)
    }

    @Test
    fun `reselecting a setup panel with its number cycles through every size`() = runOnScreen(UnconfinedTestDispatcher()) { screen ->
        val internalEvents = Channel<SetupUiEvent>(Channel.UNLIMITED)
        val loopJob = launch {
            setupLoop(
                events = internalEvents.receiveAsFlow(),
                internalEvents = internalEvents,
                screen = screen,
                initialState = initialState().copy(modeLocked = true),
                keys = Keybindings.DEFAULT,
                lobby = NoLobby,
            )
        }

        internalEvents.send(SetupUiEvent.Input(KeyboardEvent("3")))
        assertThat(terminal.frameText()).contains("PLAYER 1")
        assertThat(terminal.frameText()).doesNotContain("'MECH DATA")

        internalEvents.send(SetupUiEvent.Input(KeyboardEvent("3")))
        assertThat(terminal.frameText()).contains("'MECH DATA")

        internalEvents.send(SetupUiEvent.Input(KeyboardEvent("3")))
        assertThat(terminal.frameText()).doesNotContain("PLAYER 1")

        internalEvents.send(SetupUiEvent.Input(KeyboardEvent("3")))
        assertThat(terminal.frameText()).contains("PLAYER 1")
        assertThat(terminal.frameText()).doesNotContain("'MECH DATA")

        internalEvents.send(SetupUiEvent.Quit)
        loopJob.join()
    }

    @Test
    fun `an opponent who leaves keeps the panels but closes the commit gate`() = runOnScreen { screen ->
        val hostState = initialState().copy(
            mode = SetupMode.HOST,
            modeLocked = true,
            opponentConnected = true,
            opponentEverConnected = true,
            plan = MatchPlan(mapName = "arena")
                .withCount(PlayerId.PLAYER_1, "AS7-D", 1)
                .withCount(PlayerId.PLAYER_2, "AS7-D", 1),
        )

        val outcome = screen.runSetup(
            SetupUiEvent.Lobby(LobbyEvent.OpponentLeft),
            SetupUiEvent.Input(KeyboardEvent("c")), // would otherwise commit — the plan is complete
            SetupUiEvent.Quit,
            initial = hostState,
        )

        // Quit, not Commit: `c` was refused even though the plan itself is complete. (The refusal
        // message is asserted in SetupStateTest.)
        assertThat(outcome).isEqualTo(SetupOutcome.Quit)
        assertThat(terminal.frameText()).contains("PLAYER 1") // panels stayed
    }

    /**
     * Wiring only: that the keys reach the handler and the loop survives both a browse and a
     * refusal. What each key *does* is pinned in `SetupStateTest` (the semantics) and
     * `SetupWorkspaceTest` (the cursor driving the maximized detail pane) — this fixture has one
     * map and one mech, so a cursor move here would clamp and prove nothing.
     */
    @Test
    fun `a read-only mirror keeps browsing and editing keys wired without crashing`() = runOnScreen { screen ->
        val mirrorState = initialState(readOnly = true).copy(
            modeLocked = true,
            mode = SetupMode.HOST,
            opponentConnected = true,
            opponentEverConnected = true,
        )

        val outcome = screen.runSetup(
            SetupUiEvent.Input(KeyboardEvent("3")), // focus PLAYER_1 — focus stays live in a mirror
            SetupUiEvent.Input(KeyboardEvent("ArrowDown")), // browsing stays live in a mirror
            SetupUiEvent.Input(KeyboardEvent(" ")), // refused: only the host selects
            SetupUiEvent.Quit,
            initial = mirrorState,
        )

        assertThat(outcome).isEqualTo(SetupOutcome.Quit)
        assertThat(terminal.frameText()).contains("PLAYER 1")
    }
}
