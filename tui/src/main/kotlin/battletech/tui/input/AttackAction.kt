package battletech.tui.input

import tenter.input.InputAction

public sealed interface AttackAction : InputAction {
    public data class TwistTorso(val clockwise: Boolean) : AttackAction {
        override val id: String get() = if (clockwise) "twistTorso.cw" else "twistTorso.ccw"
    }

    public data class NavigateWeapons(val delta: Int) : AttackAction {
        override val id: String get() = if (delta < 0) "navigateWeapons.up" else "navigateWeapons.down"
    }

    public data object ToggleWeapon : AttackAction {
        override val id: String get() = "toggleWeapon"
    }

    public data object NextAttacker : AttackAction {
        override val id: String get() = "nextAttacker"
    }

    public data object Cancel : AttackAction {
        override val id: String get() = "cancel"
    }

    public data object Commit : AttackAction {
        override val id: String get() = "commit"
    }
}
