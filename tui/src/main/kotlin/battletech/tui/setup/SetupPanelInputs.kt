package battletech.tui.setup

import battletech.tactical.model.PlayerId
import battletech.tui.input.ContextId
import battletech.tui.input.Keybindings
import tenter.input.KeySection
import tenter.panel.Panel
import tenter.text.CellWidth
import tenter.view.View

/**
 * The per-frame view-model for the setup screen, mirroring `battletech.tui.view.PanelInputs`:
 * fields are `lazy` so a hidden panel's view is never built.
 */
internal class SetupPanelInputs(private val state: SetupState, private val keys: Keybindings) {

    val modeView: View by lazy {
        ModePanelView(
            mode = state.mode,
            modeLocked = state.modeLocked,
            endpoint = state.endpoint,
            opponentConnected = state.opponentConnected,
            cursorIndex = state.cursors[SetupPanelId.MODE] ?: state.mode.ordinal,
        )
    }

    val minimizedModeView: View by lazy {
        ModePanelView(
            mode = state.mode,
            modeLocked = state.modeLocked,
            endpoint = state.endpoint,
            opponentConnected = state.opponentConnected,
            cursorIndex = state.cursors[SetupPanelId.MODE] ?: state.mode.ordinal,
            compact = true,
        )
    }

    val mapView: View by lazy {
        MapListView(
            maps = state.catalog.maps,
            selected = state.plan.mapName,
            cursorIndex = state.cursors[SetupPanelId.MAP] ?: 0,
        )
    }

    private val maximizedMapSelection: MapSelectionMaximizedView by lazy {
        MapSelectionMaximizedView(
            maps = state.catalog.maps,
            selected = state.plan.mapName,
            cursorIndex = state.cursors[SetupPanelId.MAP] ?: 0,
            mapFor = state.registry::map,
        )
    }

    val maximizedMapView: View get() = maximizedMapSelection
    val maximizedMapExtent get() = maximizedMapSelection.contentExtent

    val player1View: View by lazy { playerView(PlayerId.PLAYER_1, SetupPanelId.PLAYER_1) }
    val player2View: View by lazy { playerView(PlayerId.PLAYER_2, SetupPanelId.PLAYER_2) }

    val minimizedPlayer1View: View by lazy { minimizedPlayerView(PlayerId.PLAYER_1, SetupPanelId.PLAYER_1) }
    val minimizedPlayer2View: View by lazy { minimizedPlayerView(PlayerId.PLAYER_2, SetupPanelId.PLAYER_2) }

    private val maximizedPlayer1Selection: MechSelectionMaximizedView by lazy {
        maximizedPlayerView(PlayerId.PLAYER_1, SetupPanelId.PLAYER_1)
    }
    private val maximizedPlayer2Selection: MechSelectionMaximizedView by lazy {
        maximizedPlayerView(PlayerId.PLAYER_2, SetupPanelId.PLAYER_2)
    }

    val maximizedPlayer1View: View get() = maximizedPlayer1Selection
    val maximizedPlayer1Extent get() = maximizedPlayer1Selection.contentExtent
    val maximizedPlayer2View: View get() = maximizedPlayer2Selection
    val maximizedPlayer2Extent get() = maximizedPlayer2Selection.contentExtent

    val minimizedModeWidth: Int
        get() = compactListPanelWidth(SetupMode.entries.map(ModePanelView::label))

    val minimizedMapWidth: Int
        get() = compactListPanelWidth(state.catalog.maps, emptyMessage = "No maps registered")

    fun minimizedPlayerWidth(player: PlayerId): Int = compactListPanelWidth(
        names = state.catalog.mechs,
        rightWidths = state.catalog.mechs.map { counts(player, it).toString().length },
        emptyMessage = "No mechs registered",
    )

    val helpSections: List<KeySection> by lazy {
        listOf(keys.hints(ContextId.SETUP), keys.hints(ContextId.CHROME))
    }

    private fun playerView(player: PlayerId, panel: SetupPanelId): View = UnitListView(
        variants = state.catalog.mechs,
        counts = { variant -> state.plan.count(player, variant) },
        cursorIndex = state.cursors[panel] ?: 0,
        mechFor = state.registry::mech,
    )

    private fun minimizedPlayerView(player: PlayerId, panel: SetupPanelId): View = UnitListView(
        variants = state.catalog.mechs,
        counts = { variant -> state.plan.count(player, variant) },
        cursorIndex = state.cursors[panel] ?: 0,
    )

    private fun maximizedPlayerView(player: PlayerId, panel: SetupPanelId): MechSelectionMaximizedView = MechSelectionMaximizedView(
        variants = state.catalog.mechs,
        counts = { variant -> state.plan.count(player, variant) },
        cursorIndex = state.cursors[panel] ?: 0,
        mechFor = state.registry::mech,
    )

    private fun counts(player: PlayerId, variant: String): Int = state.plan.count(player, variant)
}

private fun compactListPanelWidth(
    names: List<String>,
    rightWidths: List<Int> = emptyList(),
    emptyMessage: String = "",
): Int {
    val widestName = names.maxOfOrNull(CellWidth::of) ?: CellWidth.of(emptyMessage)
    val widestRight = rightWidths.maxOrNull() ?: 0
    val rowWidth = 4 + widestName + if (widestRight > 0) 1 + widestRight else 0
    return (rowWidth + 4).coerceAtLeast(Panel.MINIMIZED_WIDTH)
}
