package battletech.tui.view

import battletech.tui.game.PanelId

/**
 * Static description of one side panel: its stable [id], its [title], its
 * expanded [width], and how to [build] its view from the per-frame [PanelFrame].
 *
 * A panel carries no visibility logic — [battletech.tui.game.PanelVisibility]
 * decides what shows — and never sees `AppState`; it builds from the prepared
 * data on [PanelFrame].
 */
internal class Panel(
    val id: PanelId,
    val title: String,
    val width: Int,
    val build: (PanelFrame) -> View?,
)
