package battletech.tui.game.phase

import battletech.tactical.action.TurnPhase

public val TurnPhase.isAttack: Boolean
    get() = this == TurnPhase.WEAPON_ATTACK || this == TurnPhase.PHYSICAL_ATTACK

public val TurnPhase.next: TurnPhase
    get() = when (this) {
        TurnPhase.INITIATIVE -> TurnPhase.MOVEMENT
        TurnPhase.MOVEMENT -> TurnPhase.WEAPON_ATTACK
        TurnPhase.WEAPON_ATTACK -> TurnPhase.PHYSICAL_ATTACK
        TurnPhase.PHYSICAL_ATTACK -> TurnPhase.HEAT
        TurnPhase.HEAT -> TurnPhase.END
        TurnPhase.END -> TurnPhase.INITIATIVE
    }
