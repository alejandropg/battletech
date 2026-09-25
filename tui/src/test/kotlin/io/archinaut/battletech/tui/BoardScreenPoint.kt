package io.archinaut.battletech.tui

import io.archinaut.battletech.tui.game.AppState
import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.battletech.tui.view.Workspace
import io.archinaut.tenter.screen.Point

/** Locates content through completed-frame hits, without depending on toolkit chrome insets. */
internal fun boardScreenPoint(state: AppState, contentX: Int, contentY: Int): Point {
    val workspace = Workspace(Keybindings.DEFAULT)
    val buffer = workspace.render(state, 120, 40, flash = null)
    for (y in 0 until buffer.height) {
        for (x in 0 until buffer.width) {
            val hit = workspace.hitTest(x, y)
            if (hit?.id == GamePanelId.BOARD && hit.contentPoint == Point(contentX, contentY)) return Point(x, y)
        }
    }
    error("Board content ($contentX, $contentY) is not visible")
}
