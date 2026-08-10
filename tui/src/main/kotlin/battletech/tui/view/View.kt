package battletech.tui.view

import battletech.tui.screen.Canvas

internal interface View {
    public fun render(canvas: Canvas)
}
