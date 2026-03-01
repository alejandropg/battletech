package battletech.tui.input

import battletech.tactical.model.HexCoordinates

public sealed interface AttackAction {
    public data class TwistTorso(val clockwise: Boolean) : AttackAction
    public data class NavigateWeapons(val delta: Int) : AttackAction
    public data object ToggleWeapon : AttackAction
    public data object NextTarget : AttackAction
    public data object Confirm : AttackAction
    public data object Cancel : AttackAction
    public data class ClickTarget(val coords: HexCoordinates) : AttackAction
}
