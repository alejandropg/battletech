package battletech.tui.setup

import battletech.tui.input.Keybindings
import tenter.panel.Panel
import tenter.panel.PanelSet
import tenter.view.HelpView

internal typealias SetupPanel = Panel<SetupPanelId, SetupPanelInputs>
internal typealias SetupPanelSet = PanelSet<SetupPanelId, SetupPanelInputs>

/**
 * Builds this run's [SetupPanelSet]: a fresh, uniform-layout set of instances per call — each
 * [Panel] is stateful, and must live for exactly one [SetupWorkspace]'s lifetime, never longer
 * (see [Panel]'s KDoc). Declaration order MODE, MAP, PLAYER_1, PLAYER_2, HELP is also the layout
 * order for the uniform columns.
 */
internal object SetupPanels {
    fun build(keys: Keybindings): SetupPanelSet {
        val panels = listOf(
            SetupPanel(
                id = SetupPanelId.MODE,
                title = "MODE",
                normalWidth = 0,
                badge = keys.badgeFor(SetupPanelId.MODE),
                normal = { it.modeView },
            ),
            SetupPanel(
                id = SetupPanelId.MAP,
                title = "MAP",
                normalWidth = 0,
                badge = keys.badgeFor(SetupPanelId.MAP),
                normal = { it.mapView },
            ),
            SetupPanel(
                id = SetupPanelId.PLAYER_1,
                title = "PLAYER 1",
                normalWidth = 0,
                badge = keys.badgeFor(SetupPanelId.PLAYER_1),
                normal = { it.player1View },
            ),
            SetupPanel(
                id = SetupPanelId.PLAYER_2,
                title = "PLAYER 2",
                normalWidth = 0,
                badge = keys.badgeFor(SetupPanelId.PLAYER_2),
                normal = { it.player2View },
            ),
            SetupPanel(
                id = SetupPanelId.HELP,
                title = HelpView.TITLE,
                normalWidth = 0,
                badge = keys.badgeFor(SetupPanelId.HELP),
                normal = { HelpView(it.helpSections) },
                maximized = { HelpView(it.helpSections) },
            ),
        )
        return PanelSet(panels)
    }
}
