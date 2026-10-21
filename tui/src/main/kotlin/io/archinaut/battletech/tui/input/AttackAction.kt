package io.archinaut.battletech.tui.input


public sealed interface AttackAction : Action {
    public data class TwistTorso(val clockwise: Boolean) : AttackAction

    public data class NavigateWeapons(val delta: Int) : AttackAction

    public data object ToggleWeapon : AttackAction

    public data object NextAttacker : AttackAction

    public data object Cancel : AttackAction

    public data object Commit : AttackAction
}
