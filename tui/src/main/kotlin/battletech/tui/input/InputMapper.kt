package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tui.game.PanelScroll
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
        "ArrowLeft", "q" -> HexDirection.NW
        "e" -> HexDirection.NE
        "a" -> HexDirection.SW
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

    public fun isCollapseToggle(event: KeyboardEvent): Int? {
        if (!event.alt) return null
        return event.key.singleOrNull()?.digitToIntOrNull()
    }

    /**
     * Returns the scroll delta for a mouse event: negative for scroll-up, positive for
     * scroll-down, null when the event carries no scroll intent.
     *
     * [overPanel] must be true when the pointer is over a scrollable panel slot; false
     * when it is over the board or any other non-panel area.
     *
     * ### Mordant 3.0.2 wheel-parsing workaround
     * Mordant's posix event parser checks `wheelUp = cb == 64` and `wheelDown = cb == 65`,
     * but real terminals in X10/1005 encoding (which Mordant enables) transmit wheel events
     * as button-code-plus-32: cb 96 (wheel up) and cb 97 (wheel down).
     * `96 and 3 == 0` → Mordant decodes wheel-up as `left = true`; `97 and 3 == 1` →
     * `right = true`.  Consequently, on any real posix terminal `wheelUp` and `wheelDown`
     * are never true and a physical wheel tick is indistinguishable from a button press.
     *
     * Workaround: over a scrollable panel slot, treat a left press as wheel-up and a right
     * press as wheel-down.  Panels have no click semantics of their own, so nothing is lost.
     * Board click handling (`mapMouseToHex`) requires left and is unaffected because it is
     * reached only when [overPanel] is false.  The canonical `wheelUp`/`wheelDown` branches
     * keep precedence so the code is correct on Windows and future-proof once Mordant is
     * patched upstream.
     *
     * A button *release* arrives with all button flags false and yields null.
     */
    public fun scrollDelta(event: MouseEvent, overPanel: Boolean): Int? = when {
        event.wheelUp -> -PanelScroll.STEP
        event.wheelDown -> PanelScroll.STEP
        overPanel && event.left -> -PanelScroll.STEP
        overPanel && event.right -> PanelScroll.STEP
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
