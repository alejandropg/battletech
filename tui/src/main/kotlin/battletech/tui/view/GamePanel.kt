package battletech.tui.view

import battletech.tui.game.GamePanelId
import tenter.panel.Panel
import tenter.panel.PanelLayout

/** This app's own instantiation of tenter's generic [Panel] — one side panel keyed by [GamePanelId], built from [PanelInputs]. */
internal typealias GamePanel = Panel<GamePanelId, PanelInputs>

/** This app's own instantiation of tenter's generic [PanelLayout], over [GamePanel]s. */
internal typealias GamePanelLayout = PanelLayout<GamePanelId, PanelInputs>
