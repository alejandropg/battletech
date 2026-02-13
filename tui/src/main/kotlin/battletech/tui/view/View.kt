package battletech.tui.view

import battletech.tui.screen.ScreenBuffer

public interface View {
    public fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int)
}
