package battletech.tui.setup

import battletech.tui.input.ChromeAction
import battletech.tui.input.Keybindings
import battletech.tui.view.helpPanel
import battletech.tactical.model.PlayerId
import tenter.panel.Panel
import tenter.panel.PanelSet

internal typealias SetupPanel = Panel<SetupPanelId, SetupPanelInputs>
internal typealias SetupPanelSet = PanelSet<SetupPanelId, SetupPanelInputs>

/**
 * Builds this run's [SetupPanelSet]: a fresh, uniform-layout set of instances per call — each
 * [Panel] is stateful, and must live for exactly one [SetupWorkspace]'s lifetime, never longer
 * (see [Panel]'s KDoc). Declaration order MODE, MAP, PLAYER_1, PLAYER_2, HELP is also the layout
 * order: the first four occupy the proportional setup grid and HELP is the fixed trailing panel.
 */
internal object SetupPanels {
    fun build(keys: Keybindings): SetupPanelSet {
        val panels = listOf(
            SetupPanel(
                id = SetupPanelId.MODE,
                title = "MODE",
                badge = keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.MODE)).toString(),
                normal = { Panel.Presentation(it.modeView, width = 0) },
                minimized = { Panel.Presentation(it.minimizedModeView, it.minimizedModeWidth) },
            ),
            SetupPanel(
                id = SetupPanelId.MAP,
                title = "MAP",
                badge = keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.MAP)).toString(),
                normal = { Panel.Presentation(it.mapView, width = 0) },
                maximized = { Panel.Presentation(it.maximizedMapView, width = 0) },
                minimized = { Panel.Presentation(it.mapView, it.minimizedMapWidth) },
            ),
            SetupPanel(
                id = SetupPanelId.PLAYER_1,
                title = "PLAYER 1",
                badge = keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.PLAYER_1)).toString(),
                normal = { Panel.Presentation(it.player1View, width = 0) },
                maximized = { Panel.Presentation(it.maximizedPlayer1View, width = 0) },
                minimized = { Panel.Presentation(it.minimizedPlayer1View, it.minimizedPlayerWidth(PlayerId.PLAYER_1)) },
            ),
            SetupPanel(
                id = SetupPanelId.PLAYER_2,
                title = "PLAYER 2",
                badge = keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.PLAYER_2)).toString(),
                normal = { Panel.Presentation(it.player2View, width = 0) },
                maximized = { Panel.Presentation(it.maximizedPlayer2View, width = 0) },
                minimized = { Panel.Presentation(it.minimizedPlayer2View, it.minimizedPlayerWidth(PlayerId.PLAYER_2)) },
            ),
            helpPanel(
                id = SetupPanelId.HELP,
                badge = keys.badgeFor(ChromeAction.ToggleHelp),
                sections = { it.helpSections },
            ),
        )
        return PanelSet.uniform(panels)
    }
}
