package battletech.tui.input

import battletech.tactical.model.HexDirection
import battletech.tui.hex.HexLayout
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

public object InputMapper {

    public fun mapKeyboardEvent(event: KeyboardEvent): InputAction? {
        if (event.ctrl && event.key == "c") return InputAction.Quit

        return when (event.key) {
            "ArrowUp" -> InputAction.MoveCursor(HexDirection.N)
            "ArrowDown" -> InputAction.MoveCursor(HexDirection.S)
            "ArrowRight" -> InputAction.MoveCursor(HexDirection.SE)
            "ArrowLeft" -> InputAction.MoveCursor(HexDirection.NW)
            "Enter" -> InputAction.Confirm
            "Escape" -> InputAction.Cancel
            "Tab" -> InputAction.CycleUnit
            "q" -> InputAction.Quit
            in "1".."9" -> InputAction.SelectAction(event.key.toInt())
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
