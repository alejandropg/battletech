package battletech.tui.input

import battletech.tui.hex.HexLayout
import battletech.tactical.model.HexDirection

public object InputMapper {

    public fun mapKeyboardEvent(key: String, ctrl: Boolean, alt: Boolean): InputAction? {
        if (ctrl && key == "c") return InputAction.Quit

        return when (key) {
            "ArrowUp" -> InputAction.MoveCursor(HexDirection.N)
            "ArrowDown" -> InputAction.MoveCursor(HexDirection.S)
            "ArrowRight" -> InputAction.MoveCursor(HexDirection.SE)
            "ArrowLeft" -> InputAction.MoveCursor(HexDirection.NW)
            "Enter" -> InputAction.Confirm
            "Escape" -> InputAction.Cancel
            "Tab" -> InputAction.CycleUnit
            "q" -> InputAction.Quit
            in "1".."9" -> InputAction.SelectAction(key.toInt())
            else -> null
        }
    }

    public fun mapMouseEvent(x: Int, y: Int, button: Int, scrollX: Int, scrollY: Int): InputAction? {
        val coords = HexLayout.screenToHex(x, y, scrollX, scrollY) ?: return null
        return InputAction.ClickHex(coords)
    }
}
