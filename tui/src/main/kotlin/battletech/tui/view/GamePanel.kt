package battletech.tui.view

import battletech.tui.game.GamePanelId
import tenter.panel.Panel
import tenter.panel.PanelSet

/** This app's own instantiation of tenter's generic [Panel] — one panel keyed by [GamePanelId], built from [PanelInputs]. */
internal typealias GamePanel = Panel<GamePanelId, PanelInputs>

/** This app's own instantiation of tenter's generic [PanelSet], over [GamePanel]s. */
internal typealias GamePanelSet = PanelSet<GamePanelId, PanelInputs>
