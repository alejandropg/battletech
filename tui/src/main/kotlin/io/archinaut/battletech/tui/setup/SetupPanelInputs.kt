package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tui.input.ContextId
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.input.KeySection
import io.archinaut.battletech.tui.view.GamePanels
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.MeasurableWidget

/**
 * The per-frame view-model for the setup screen, mirroring `io.archinaut.battletech.tui.view.PanelInputs`:
 * fields are `lazy` so a hidden panel's view is never built.
 */
internal class SetupPanelInputs(private val state: SetupState, private val keys: Keybindings) {

    val modeView: MeasurableWidget by lazy {
        ModePanelView(
            mode = state.mode,
            modeLocked = state.modeLocked,
            endpoint = state.endpoint,
            opponentConnected = state.opponentConnected,
            cursorIndex = state.cursors[SetupPanelId.MODE] ?: state.mode.ordinal,
        )
    }

    val minimizedModeView: MeasurableWidget by lazy {
        ModePanelView(
            mode = state.mode,
            modeLocked = state.modeLocked,
            endpoint = state.endpoint,
            opponentConnected = state.opponentConnected,
            cursorIndex = state.cursors[SetupPanelId.MODE] ?: state.mode.ordinal,
            compact = true,
        )
    }

    val mapView: MeasurableWidget by lazy {
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

    val maximizedMapView: MeasurableWidget get() = maximizedMapSelection

    val player1View: MeasurableWidget by lazy { playerView(PlayerId.PLAYER_1, SetupPanelId.PLAYER_1) }
    val player2View: MeasurableWidget by lazy { playerView(PlayerId.PLAYER_2, SetupPanelId.PLAYER_2) }

    val minimizedPlayer1View: MeasurableWidget by lazy { minimizedPlayerView(PlayerId.PLAYER_1, SetupPanelId.PLAYER_1) }
    val minimizedPlayer2View: MeasurableWidget by lazy { minimizedPlayerView(PlayerId.PLAYER_2, SetupPanelId.PLAYER_2) }

    private val maximizedPlayer1Selection: MechSelectionMaximizedView by lazy {
        maximizedPlayerView(PlayerId.PLAYER_1, SetupPanelId.PLAYER_1)
    }
    private val maximizedPlayer2Selection: MechSelectionMaximizedView by lazy {
        maximizedPlayerView(PlayerId.PLAYER_2, SetupPanelId.PLAYER_2)
    }

    val maximizedPlayer1View: MeasurableWidget get() = maximizedPlayer1Selection
    val maximizedPlayer2View: MeasurableWidget get() = maximizedPlayer2Selection

    /** The outer width of [panel]'s minimized compact list, sized to fit this frame's content. */
    fun minimizedWidth(panel: SetupPanelId): Int = when (panel) {
        SetupPanelId.MODE -> compactListPanelWidth(SetupMode.entries.map(ModePanelView::label))
        SetupPanelId.MAP -> compactListPanelWidth(state.catalog.maps, emptyMessage = "No maps registered")
        SetupPanelId.PLAYER_1 -> minimizedPlayerWidth(PlayerId.PLAYER_1)
        SetupPanelId.PLAYER_2 -> minimizedPlayerWidth(PlayerId.PLAYER_2)
        SetupPanelId.HELP -> error("HELP has no minimized mode")
    }

    private fun minimizedPlayerWidth(player: PlayerId): Int = compactListPanelWidth(
        names = state.catalog.mechs,
        rightWidths = state.catalog.mechs.map { counts(player, it).toString().length },
        emptyMessage = "No mechs registered",
    )

    val helpSections: List<KeySection> by lazy {
        listOf(keys.hints(ContextId.SETUP), keys.hints(ContextId.CHROME))
    }

    private fun playerView(player: PlayerId, panel: SetupPanelId): MeasurableWidget = UnitListView(
        variants = state.catalog.mechs,
        counts = { variant -> state.plan.count(player, variant) },
        cursorIndex = state.cursors[panel] ?: 0,
        mechFor = state.registry::mech,
    )

    private fun minimizedPlayerView(player: PlayerId, panel: SetupPanelId): MeasurableWidget = UnitListView(
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
    return (rowWidth + 4).coerceAtLeast(GamePanels.MINIMIZED_WIDTH)
}
