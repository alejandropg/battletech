package battletech.tui.setup

import battletech.tactical.model.PlayerId
import battletech.tui.input.ContextId
import battletech.tui.input.Keybindings
import tenter.input.KeySection
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
        )
    }

    val mapView: View by lazy {
        MapListView(
            maps = state.catalog.maps,
            selected = state.plan.mapName,
            cursorIndex = state.cursors[SetupPanelId.MAP] ?: 0,
        )
    }

    val player1View: View by lazy { playerView(PlayerId.PLAYER_1, SetupPanelId.PLAYER_1) }
    val player2View: View by lazy { playerView(PlayerId.PLAYER_2, SetupPanelId.PLAYER_2) }

    val helpSections: List<KeySection> by lazy {
        listOf(keys.hints(ContextId.SETUP), keys.hints(ContextId.CHROME))
    }

    private fun playerView(player: PlayerId, panel: SetupPanelId): View = UnitListView(
        variants = state.catalog.mechs,
        counts = { variant -> state.plan.count(player, variant) },
        cursorIndex = state.cursors[panel] ?: 0,
    )
}
