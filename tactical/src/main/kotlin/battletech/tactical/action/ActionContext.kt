package battletech.tactical.action

import battletech.tactical.model.GameState
import battletech.tactical.model.Weapon

public data class ActionContext(
    val actor: Unit,
    val target: Unit? = null,
    val weapon: Weapon? = null,
    val gameState: GameState,
)
