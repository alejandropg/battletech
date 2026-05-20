package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tui.hex.HexLayout
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

public object InputMapper {

    public fun isQuit(event: KeyboardEvent): Boolean =
        event.ctrl && event.key == "c"

    private fun keyToDirection(key: String): HexDirection? = when (key) {
        "ArrowUp", "w" -> HexDirection.N
        "ArrowDown", "s" -> HexDirection.S
        "ArrowRight", "d" -> HexDirection.SE
        "ArrowLeft", "a" -> HexDirection.NW
        "e" -> HexDirection.NE
        "q" -> HexDirection.NW
        else -> null
    }

    private fun facingNumber(key: String): Int? =
        key.singleOrNull()?.takeIf { it in '1'..'6' }?.digitToInt()

    public fun mapIdleEvent(event: KeyboardEvent): IdleAction? =
        keyToDirection(event.key)?.let(IdleAction::MoveCursor)
            ?: when (event.key) {
                "Enter" -> IdleAction.SelectUnit
                "Tab" -> IdleAction.CycleUnit
                "c" -> IdleAction.CommitDeclarations
                else -> null
            }

    public fun mapBrowsingEvent(event: KeyboardEvent): BrowsingAction? {
        keyToDirection(event.key)?.let { return BrowsingAction.MoveCursor(it) }
        facingNumber(event.key)?.let { return BrowsingAction.SelectFacing(it) }
        return when (event.key) {
            "Enter" -> BrowsingAction.ConfirmPath
            "Escape" -> BrowsingAction.Cancel
            "Tab" -> BrowsingAction.CycleUnit
            "x" -> BrowsingAction.CycleMode
            else -> null
        }
    }

    public fun mapFacingEvent(event: KeyboardEvent): FacingAction? {
        facingNumber(event.key)?.let { return FacingAction.SelectFacing(it) }
        return when (event.key) {
            "Escape" -> FacingAction.Cancel
            "Tab" -> FacingAction.CycleUnit
            else -> null
        }
    }

    public fun mapAttackEvent(event: KeyboardEvent): AttackAction? = when (event.key) {
        "ArrowRight" -> AttackAction.TwistTorso(clockwise = true)
        "ArrowLeft" -> AttackAction.TwistTorso(clockwise = false)
        "ArrowUp" -> AttackAction.NavigateWeapons(delta = -1)
        "ArrowDown" -> AttackAction.NavigateWeapons(delta = 1)
        " " -> AttackAction.ToggleWeapon
        "Tab" -> AttackAction.NextAttacker
        "c" -> AttackAction.Commit
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
