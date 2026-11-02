package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tui.view.drawFrame
import io.archinaut.tenter.terminal.CompletedFrame
import io.archinaut.tenter.widget.FlashMessage

/** Draws one [width]x[height] frame of [state] through this workspace and returns what it drew. */
internal fun SetupWorkspace.draw(state: SetupState, width: Int, height: Int, flash: FlashMessage?): CompletedFrame =
    drawFrame(width, height) { render(it, state, flash) }
