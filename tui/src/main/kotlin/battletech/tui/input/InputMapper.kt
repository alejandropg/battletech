package battletech.tui.input

import battletech.tui.hex.HexLayout
import battletech.tactical.model.HexDirection
import com.github.ajalt.mordant.input.MouseEvent

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

    public fun mapMouseEvent(event: MouseEvent, boardX: Int, boardY: Int): InputAction? {
        if (!event.left && !event.right && !event.middle) return null
        if (!event.left) return null

        val x = event.x - boardX
        val y = event.y - boardY
        if (x < 0 || y < 0) return null

        val coords = HexLayout.screenToHex(x, y, scrollX = 0, scrollY = 0) ?: return null
        return InputAction.ClickHex(coords)
    }
}
