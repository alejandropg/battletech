package battletech.tui.view

import battletech.tui.game.PanelId
import tenter.panel.Panel
import tenter.panel.PanelLayout

/** This app's own instantiation of tenter's generic [Panel] — one side panel keyed by [PanelId], built from [PanelInputs]. */
internal typealias GamePanel = Panel<PanelId, PanelInputs>

/** This app's own instantiation of tenter's generic [PanelLayout], over [GamePanel]s. */
internal typealias GamePanelLayout = PanelLayout<PanelId, PanelInputs>
