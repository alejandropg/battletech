package battletech.tui.view

import battletech.tui.game.PanelId

/**
 * Static description of one side panel: its stable [id], its [title], its expanded [width], the
 * [collapsedWidth] it shrinks to when the user collapses it (`0` removes it from the layout
 * entirely — no stub, no box, its columns returned to the board; HELP is the only panel that does
 * this, closed by default and opened on demand rather than shrunk to a stub), and how to [build]
 * its view from the per-frame [PanelFrame].
 *
 * A panel carries no visibility logic — [battletech.tui.game.PanelVisibility]
 * decides what shows — and never sees `AppState`; it builds from the prepared
 * data on [PanelFrame].
 */
internal class Panel(
    val id: PanelId,
    val title: String,
    val width: Int,
    val collapsedWidth: Int = FrameLayout.COLLAPSED_STUB_WIDTH,
    val build: (PanelFrame) -> View?,
)
