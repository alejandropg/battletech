package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.tenter.panel.Panel
import io.archinaut.tenter.panel.PanelSet

/** This app's own instantiation of tenter's generic [Panel] — one panel keyed by [GamePanelId], built from [PanelInputs]. */
internal typealias GamePanel = Panel<GamePanelId, PanelInputs>

/** This app's own instantiation of tenter's generic [PanelSet], over [GamePanel]s. */
internal typealias GamePanelSet = PanelSet<GamePanelId, PanelInputs>
