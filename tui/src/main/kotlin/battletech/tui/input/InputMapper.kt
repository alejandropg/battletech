package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tui.hex.HexLayout
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

public object InputMapper {

    public fun isQuit(event: KeyboardEvent): Boolean =
        event.key == "q" || (event.ctrl && event.key == "c")

    public fun mapIdleEvent(event: KeyboardEvent): IdleAction? = when (event.key) {
        "ArrowUp" -> IdleAction.MoveCursor(HexDirection.N)
        "ArrowDown" -> IdleAction.MoveCursor(HexDirection.S)
        "ArrowRight" -> IdleAction.MoveCursor(HexDirection.SE)
        "ArrowLeft" -> IdleAction.MoveCursor(HexDirection.NW)
        "Enter" -> IdleAction.SelectUnit
        "Tab" -> IdleAction.CycleUnit
        "c" -> IdleAction.CommitDeclarations
        else -> null
    }

    public fun mapBrowsingEvent(event: KeyboardEvent): BrowsingAction? = when (event.key) {
        "ArrowUp" -> BrowsingAction.MoveCursor(HexDirection.N)
        "ArrowDown" -> BrowsingAction.MoveCursor(HexDirection.S)
        "ArrowRight" -> BrowsingAction.MoveCursor(HexDirection.SE)
        "ArrowLeft" -> BrowsingAction.MoveCursor(HexDirection.NW)
        "Enter" -> BrowsingAction.ConfirmPath
        "Escape" -> BrowsingAction.Cancel
        "Tab" -> BrowsingAction.CycleMode
        in "1".."6" -> BrowsingAction.SelectFacing(event.key.toInt())
        else -> null
    }

    public fun mapFacingEvent(event: KeyboardEvent): FacingAction? = when (event.key) {
        in "1".."6" -> FacingAction.SelectFacing(event.key.toInt())
        "Escape" -> FacingAction.Cancel
        else -> null
    }

    public fun mapAttackEvent(event: KeyboardEvent): AttackAction? = when (event.key) {
        "ArrowRight" -> AttackAction.TwistTorso(clockwise = true)
        "ArrowLeft" -> AttackAction.TwistTorso(clockwise = false)
        "ArrowUp" -> AttackAction.NavigateWeapons(delta = -1)
        "ArrowDown" -> AttackAction.NavigateWeapons(delta = 1)
        " " -> AttackAction.ToggleWeapon
        "Tab" -> AttackAction.NextAttacker
        "Enter" -> AttackAction.Confirm
        "Escape" -> AttackAction.Cancel
        else -> null
    }

    public fun mapMouseToHex(event: MouseEvent, boardX: Int, boardY: Int): HexCoordinates? {
        if (!event.left) return null
        val x = event.x - boardX
        val y = event.y - boardY
        if (x < 0 || y < 0) return null
        return HexLayout.screenToHex(x, y, scrollX = 0, scrollY = 0)
    }

}
