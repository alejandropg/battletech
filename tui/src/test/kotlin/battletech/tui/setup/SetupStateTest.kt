package battletech.tui.setup

import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.ContentSummary
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.unit.AutoDeploy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Covers [handleSetup]'s behavior table, [SetupState.commitBlocker], [SetupState.rostersVisible], and [nextPanel]. */
internal class SetupStateTest {

    private val tinyMap: GameMap = GameMap(
        hexes = (0 until 4).map { row -> HexCoordinates(0, row) }.associateWith { Hex(it) },
        name = "tiny",
    )

    private val catalog = ContentSummary(maps = listOf("tiny"), mechs = listOf("AS7-D", "WHM-6R"))
    private val registry = AssetRegistry(maps = mapOf("tiny" to tinyMap))
    private val baseState = SetupState(catalog = catalog, registry = registry)

    // ---- MODE panel ----

    @Test
    fun `MODE unlocked MoveCursor moves the highlight without changing the mode`() {
        val result = handleSetup(SetupAction.MoveCursor(1), SetupPanelId.MODE, baseState)

        assertThat(result?.state?.mode).isEqualTo(SetupMode.HOT_SEAT)
        assertThat(result?.state?.cursors?.get(SetupPanelId.MODE)).isEqualTo(1)
    }

    @Test
    fun `MODE unlocked MoveCursor clamps to the mode entries`() {
        val atStart = handleSetup(SetupAction.MoveCursor(-1), SetupPanelId.MODE, baseState)!!.state
        val atEnd = handleSetup(SetupAction.MoveCursor(1), SetupPanelId.MODE, atStart)!!.state
        val stillAtEnd = handleSetup(SetupAction.MoveCursor(1), SetupPanelId.MODE, atEnd)

        assertThat(atStart.cursors[SetupPanelId.MODE]).isEqualTo(0)
        assertThat(atEnd.cursors[SetupPanelId.MODE]).isEqualTo(1)
        assertThat(stillAtEnd?.state?.cursors?.get(SetupPanelId.MODE)).isEqualTo(1)
    }

    @Test
    fun `MODE unlocked Toggle selects the highlighted mode`() {
        val moved = handleSetup(SetupAction.MoveCursor(1), SetupPanelId.MODE, baseState)!!.state
        val result = handleSetup(SetupAction.Toggle, SetupPanelId.MODE, moved)

        assertThat(result?.state?.mode).isEqualTo(SetupMode.HOST)
        assertThat(result?.state?.cursors?.get(SetupPanelId.MODE)).isEqualTo(1)
    }

    @Test
    fun `MODE Toggle uses the selected mode when its cursor is absent`() {
        val host = baseState.copy(mode = SetupMode.HOST)

        val result = handleSetup(SetupAction.Toggle, SetupPanelId.MODE, host)

        assertThat(result?.state?.mode).isEqualTo(SetupMode.HOST)
    }

    @Test
    fun `MODE unlocked Commit locks the mode in`() {
        val result = handleSetup(SetupAction.Commit, SetupPanelId.MODE, baseState)

        assertThat(result?.state?.modeLocked).isTrue()
        assertThat(result?.state?.endpoint).isNull()
    }

    @Test
    fun `MODE locked is inert to every action but commit`() {
        val locked = baseState.copy(modeLocked = true)

        assertThat(handleSetup(SetupAction.Toggle, SetupPanelId.MODE, locked)).isNull()
        assertThat(handleSetup(SetupAction.MoveCursor(1), SetupPanelId.MODE, locked)).isNull()
        // Commit is not inert here — the mode is settled, but `c` still commits the match (D8).
        assertThat(handleSetup(SetupAction.Commit, SetupPanelId.MODE, locked)?.flash).isNotNull()
    }

    // ---- MAP panel ----

    private val stage2State = baseState.copy(modeLocked = true)

    @Test
    fun `MODE locked still commits -- c is the commit key on every panel`() {
        val plan = MatchPlan(mapName = "tiny")
            .withCount(PlayerId.PLAYER_1, "AS7-D", 1)
            .withCount(PlayerId.PLAYER_2, "WHM-6R", 1)
        val state = stage2State.copy(plan = plan)

        val transition = handleSetup(SetupAction.Commit, SetupPanelId.MODE, state)

        assertThat(transition?.committed).isEqualTo(plan)
    }

    @Test
    fun `MODE locked flashes the blocker when the plan is incomplete`() {
        val transition = handleSetup(SetupAction.Commit, SetupPanelId.MODE, stage2State)

        assertThat(transition?.committed).isNull()
        assertThat(transition?.flash?.text).isEqualTo("select a map")
    }

    @Test
    fun `MAP MoveCursor clamps to the catalog's map indices`() {
        val result = handleSetup(SetupAction.MoveCursor(-5), SetupPanelId.MAP, stage2State)

        assertThat(result?.state?.cursors?.get(SetupPanelId.MAP)).isEqualTo(0)
    }

    @Test
    fun `MAP Toggle selects the cursor's map`() {
        val result = handleSetup(SetupAction.Toggle, SetupPanelId.MAP, stage2State)

        assertThat(result?.state?.plan?.mapName).isEqualTo("tiny")
    }

    @Test
    fun `MAP Toggle on the already-selected map deselects it`() {
        val selected = stage2State.copy(plan = MatchPlan(mapName = "tiny"))

        val result = handleSetup(SetupAction.Toggle, SetupPanelId.MAP, selected)

        assertThat(result?.state?.plan?.mapName).isNull()
    }

    @Test
    fun `MAP Adjust is a no-op`() {
        assertThat(handleSetup(SetupAction.Adjust(1), SetupPanelId.MAP, stage2State)).isNull()
    }

    // ---- PLAYER_n panels ----

    @Test
    fun `PLAYER_1 MoveCursor clamps to the catalog's mech indices`() {
        val result = handleSetup(SetupAction.MoveCursor(50), SetupPanelId.PLAYER_1, stage2State)

        assertThat(result?.state?.cursors?.get(SetupPanelId.PLAYER_1)).isEqualTo(1)
    }

    @Test
    fun `PLAYER_1 Toggle sets count from 0 to 1`() {
        val result = handleSetup(SetupAction.Toggle, SetupPanelId.PLAYER_1, stage2State)

        assertThat(result?.state?.plan?.count(PlayerId.PLAYER_1, "AS7-D")).isEqualTo(1)
    }

    @Test
    fun `PLAYER_1 Toggle at a positive count resets to 0`() {
        val withOne = stage2State.copy(plan = MatchPlan().withCount(PlayerId.PLAYER_1, "AS7-D", 3))

        val result = handleSetup(SetupAction.Toggle, SetupPanelId.PLAYER_1, withOne)

        assertThat(result?.state?.plan?.count(PlayerId.PLAYER_1, "AS7-D")).isZero()
    }

    @Test
    fun `PLAYER_1 Adjust plus one increments`() {
        val result = handleSetup(SetupAction.Adjust(1), SetupPanelId.PLAYER_1, stage2State)

        assertThat(result?.state?.plan?.count(PlayerId.PLAYER_1, "AS7-D")).isEqualTo(1)
        assertThat(result?.flash).isNull()
    }

    @Test
    fun `PLAYER_1 Adjust minus one floors at zero`() {
        val result = handleSetup(SetupAction.Adjust(-1), SetupPanelId.PLAYER_1, stage2State)

        assertThat(result?.state?.plan?.count(PlayerId.PLAYER_1, "AS7-D")).isZero()
    }

    @Test
    fun `PLAYER_1 Adjust plus one is refused with a flash at map capacity`() {
        // tinyMap has 4 hexes total; P1's half (odd? no, height 4, half 2 rows) has some capacity.
        // Drive the count up to whatever that capacity is, then confirm the NEXT increment refuses.
        var state = stage2State.copy(plan = MatchPlan(mapName = "tiny"))
        val capacity = AutoDeploy.capacity(tinyMap, PlayerId.PLAYER_1)
        repeat(capacity) {
            state = handleSetup(SetupAction.Adjust(1), SetupPanelId.PLAYER_1, state)!!.state
        }

        val result = handleSetup(SetupAction.Adjust(1), SetupPanelId.PLAYER_1, state)!!

        assertThat(result.flash?.text).isEqualTo("no room on the map for more units")
        assertThat(result.state.plan.count(PlayerId.PLAYER_1, "AS7-D")).isEqualTo(capacity)
    }

    @Test
    fun `PLAYER_1 Adjust plus one is unrestricted before a map is selected`() {
        val result = handleSetup(SetupAction.Adjust(1), SetupPanelId.PLAYER_1, stage2State)

        assertThat(result?.flash).isNull()
    }

    // ---- Commit ----

    @Test
    fun `Commit on MAP with an incomplete plan flashes the blocker reason`() {
        val result = handleSetup(SetupAction.Commit, SetupPanelId.MAP, stage2State)

        assertThat(result?.flash?.text).isEqualTo("select a map")
        assertThat(result?.committed).isNull()
    }

    @Test
    fun `Commit on PLAYER_2 with a complete plan commits`() {
        val plan = MatchPlan(mapName = "tiny")
            .withCount(PlayerId.PLAYER_1, "AS7-D", 1)
            .withCount(PlayerId.PLAYER_2, "WHM-6R", 1)
        val complete = stage2State.copy(plan = plan)

        val result = handleSetup(SetupAction.Commit, SetupPanelId.PLAYER_2, complete)

        assertThat(result?.committed).isEqualTo(plan)
        assertThat(result?.flash).isNull()
    }

    @Test
    fun `NextPanel is left to the loop and never handled here`() {
        assertThat(handleSetup(SetupAction.NextPanel, SetupPanelId.MAP, stage2State)).isNull()
    }

    // ---- read-only mirror ----

    @Test
    fun `every action is inert on a read-only mirror`() {
        val mirror = stage2State.copy(readOnly = true)

        assertThat(handleSetup(SetupAction.Toggle, SetupPanelId.MAP, mirror)).isNull()
        assertThat(handleSetup(SetupAction.Adjust(1), SetupPanelId.PLAYER_1, mirror)).isNull()
        assertThat(handleSetup(SetupAction.Commit, SetupPanelId.PLAYER_1, mirror)).isNull()
        assertThat(handleSetup(SetupAction.MoveCursor(1), SetupPanelId.MAP, mirror)).isNull()
    }

    // ---- commitBlocker ----

    @Test
    fun `commitBlocker before stage 2`() {
        assertThat(baseState.commitBlocker()).isEqualTo("lock a mode first")
    }

    @Test
    fun `commitBlocker with no map selected`() {
        assertThat(stage2State.commitBlocker()).isEqualTo("select a map")
    }

    @Test
    fun `commitBlocker with player 1 empty`() {
        val state = stage2State.copy(plan = MatchPlan(mapName = "tiny").withCount(PlayerId.PLAYER_2, "AS7-D", 1))
        assertThat(state.commitBlocker()).isEqualTo("player 1 has no units")
    }

    @Test
    fun `commitBlocker with player 2 empty`() {
        val state = stage2State.copy(plan = MatchPlan(mapName = "tiny").withCount(PlayerId.PLAYER_1, "AS7-D", 1))
        assertThat(state.commitBlocker()).isEqualTo("player 2 has no units")
    }

    @Test
    fun `commitBlocker over capacity`() {
        val capacity = AutoDeploy.capacity(tinyMap, PlayerId.PLAYER_1)
        val plan = MatchPlan(mapName = "tiny")
            .withCount(PlayerId.PLAYER_1, "AS7-D", capacity + 1)
            .withCount(PlayerId.PLAYER_2, "WHM-6R", 1)
        val state = stage2State.copy(plan = plan)

        assertThat(state.commitBlocker()).isEqualTo("too many units for this map")
    }

    @Test
    fun `commitBlocker waiting for player 2 in host mode`() {
        val plan = MatchPlan(mapName = "tiny")
            .withCount(PlayerId.PLAYER_1, "AS7-D", 1)
            .withCount(PlayerId.PLAYER_2, "WHM-6R", 1)
        val state = stage2State.copy(
            mode = SetupMode.HOST,
            opponentConnected = true,
            opponentEverConnected = true,
            plan = plan,
        )

        assertThat(state.commitBlocker()).isNull()
    }

    @Test
    fun `commitBlocker is null for a complete hot-seat plan`() {
        val plan = MatchPlan(mapName = "tiny")
            .withCount(PlayerId.PLAYER_1, "AS7-D", 1)
            .withCount(PlayerId.PLAYER_2, "WHM-6R", 1)
        val state = stage2State.copy(plan = plan)

        assertThat(state.commitBlocker()).isNull()
    }

    // ---- rostersVisible ----

    @Test
    fun `rostersVisible is true once hot-seat locks in, with no opponent needed`() {
        assertThat(stage2State.copy(mode = SetupMode.HOT_SEAT).rostersVisible).isTrue()
    }

    @Test
    fun `rostersVisible waits for the opponent in host mode`() {
        val locked = stage2State.copy(mode = SetupMode.HOST)
        assertThat(locked.rostersVisible).isFalse()
        assertThat(locked.copy(opponentConnected = true, opponentEverConnected = true).rostersVisible).isTrue()
    }

    @Test
    fun `rostersVisible survives an opponent who joined and then left`() {
        val dropped = stage2State.copy(mode = SetupMode.HOST, opponentEverConnected = true, opponentConnected = false)

        // The panels and the selections made against them stay; only the commit gate closes.
        assertThat(dropped.rostersVisible).isTrue()
        assertThat(dropped.commitBlocker()).isEqualTo("waiting for player 2")
    }

    @Test
    fun `rostersVisible is false before the mode locks`() {
        assertThat(baseState.rostersVisible).isFalse()
    }

    // ---- nextPanel ----

    @Test
    fun `nextPanel wraps from the last visible panel to the first`() {
        val visible = listOf(SetupPanelId.MODE, SetupPanelId.MAP, SetupPanelId.PLAYER_1, SetupPanelId.PLAYER_2)

        assertThat(nextPanel(SetupPanelId.PLAYER_2, visible)).isEqualTo(SetupPanelId.MODE)
        assertThat(nextPanel(SetupPanelId.MODE, visible)).isEqualTo(SetupPanelId.MAP)
    }

    @Test
    fun `nextPanel skips a hidden panel`() {
        val visible = listOf(SetupPanelId.MODE, SetupPanelId.PLAYER_2) // MAP, PLAYER_1 hidden

        assertThat(nextPanel(SetupPanelId.MODE, visible)).isEqualTo(SetupPanelId.PLAYER_2)
    }
}
